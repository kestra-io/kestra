package io.kestra.plugin.git;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.services.FlowService;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.plugin.git.services.GitService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.errors.EmptyCommitException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static java.lang.Integer.MAX_VALUE;
import static org.eclipse.jgit.transport.RemoteRefUpdate.Status.*;

@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Unidirectional namespace sync between Kestra and Git.",
    description = "Create/update is driven by 'sourceOfTruth'; delete/keep/fail is driven by 'whenMissingInSource'."
)
@Plugin(
    priority = Plugin.Priority.SECONDARY,
    examples = {
        @Example(
            title = "Sync a namespace using Git as the source of truth (destructive).",
            full = true,
            code = """
                id: git_namespace_sync
                namespace: system
                tasks:
                  - id: sync
                    type: io.kestra.plugin.git.NamespaceSync
                    namespace: system
                    sourceOfTruth: GIT
                    whenMissingInSource: DELETE
                    protectedNamespaces:
                      - system
                    url: https://github.com/fdelbrayelle/plugin-git-qa
                    username: fdelbrayelle
                    password: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                    branch: main
                    gitDirectory: kestra
                """
        ),
        @Example(
            title = "Sync a namespace using Kestra as source of truth (additive).",
            full = true,
            code = """
                id: kestra_namespace_sync
                namespace: system
                tasks:
                  - id: sync
                    type: io.kestra.plugin.git.NamespaceSync
                    namespace: system
                    sourceOfTruth: KESTRA
                    whenMissingInSource: KEEP
                    protectedNamespaces:
                      - system
                    url: https://github.com/fdelbrayelle/plugin-git-qa
                    username: fdelbrayelle
                    password: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                    branch: dev
                    # gitDirectory omitted -> repository root
                    onInvalidSyntax: WARN
                    # dryRun omitted
                """
        )
    }
)
public class NamespaceSync extends AbstractCloningTask implements RunnableTask<NamespaceSync.Output> {

    public enum SourceOfTruth {GIT, KESTRA}

    public enum WhenMissingInSource {DELETE, KEEP, FAIL}

    public enum OnInvalidSyntax {SKIP, WARN, FAIL}

    @Schema(
        title = "The branch to read from / write to (required).",
        description = "Branch prefixed with `origin/` or `refs/heads/` are not supported."
    )
    @NotNull
    private Property<String> branch;

    @Schema(
        title = "Subdirectory inside the repo used to store Kestra code and files; if empty, repo root is used.",
        description = """
            This is the base folder in your Git repository where Kestra will look for code and files.
            If you don't set it, the repo root will be used. Inside that folder, Kestra always expects
            a structure like <namespace>/flows, <namespace>/files, etc.

            | gitDirectory | namespace       | Expected Git path                        |
            | ------------ | --------------- | -----------------------------------------|
            | (not set)    | company         | company/flows/my-flow.yaml               |
            | monorepo     | system          | monorepo/system/flows/my-flow.yaml       |
            | projectA     | company.team    | projectA/company.team/flows/my-flow.yaml |"""
    )
    private Property<String> gitDirectory;

    @Schema(title = "Target namespace to sync (required).")
    @NotNull
    private Property<String> namespace;

    @Schema(title = "Select the source of truth.")
    @Builder.Default
    private Property<SourceOfTruth> sourceOfTruth = Property.ofValue(SourceOfTruth.KESTRA);

    @Schema(title = "Behavior when an object is missing from the selected source of truth.")
    @Builder.Default
    private Property<WhenMissingInSource> whenMissingInSource = Property.ofValue(WhenMissingInSource.DELETE);

    @Schema(title = "Namespaces protected from deletion regardless of policies.")
    @Builder.Default
    private Property<List<String>> protectedNamespaces = Property.ofValue(List.of("system"));

    @Schema(title = "If true, only compute the plan and output a diff without applying changes.")
    @Builder.Default
    private Property<Boolean> dryRun = Property.ofValue(false);

    @Schema(title = "Behavior when encountering invalid syntax while syncing.")
    @Builder.Default
    private Property<OnInvalidSyntax> onInvalidSyntax = Property.ofValue(OnInvalidSyntax.FAIL);

    @Schema(title = "Git commit message when pushing back to Git.")
    private Property<String> commitMessage;

    @Schema(title = "The commit author email.")
    private Property<String> authorEmail;

    @Schema(title = "The commit author name (defaults to username if null).")
    private Property<String> authorName;

    // Directory names (namespace-first structure: <namespace>/<kind>/<id>.yaml)
    private static final String FLOWS_DIR = "flows";
    private static final String FILES_DIR = "files";

    private FlowService flowService(RunContext rc) {
        return ((DefaultRunContext) rc).getApplicationContext().getBean(FlowService.class);
    }

    @Override
    public Output run(RunContext runContext) throws Exception {
        var gitService = new GitService(this);

        var rBranch = runContext.render(this.branch).as(String.class).orElseThrow(() -> new IllegalArgumentException("Branch must be explicitly set."));
        var rNamespace = runContext.render(this.namespace).as(String.class).orElseThrow(() -> new IllegalArgumentException("Namespace must be explicitly set."));

        runContext.logger().info("Now in NamespaceSync for namespace {}", rNamespace);

        var flowRepository = ((DefaultRunContext) runContext).getApplicationContext().getBean(FlowRepositoryInterface.class);
        var tenantId = runContext.flowInfo().tenantId();
        var distinctNamespaces = flowRepository.findDistinctNamespace(tenantId);
        if (!distinctNamespaces.contains(rNamespace)) {
            throw new IllegalArgumentException("The namespace does not exist in the '" + tenantId + "' tenant.");
        }

        var rGitDirectory = runContext.render(this.gitDirectory).as(String.class).orElse(null);
        var rSourceOfTruth = runContext.render(this.sourceOfTruth).as(SourceOfTruth.class).orElse(SourceOfTruth.KESTRA);
        var rWhenMissingInSource = runContext.render(this.whenMissingInSource).as(WhenMissingInSource.class).orElse(WhenMissingInSource.DELETE);
        var rDryRun = runContext.render(this.dryRun).as(Boolean.class).orElse(false);
        var rOnInvalidSyntax = runContext.render(this.onInvalidSyntax).as(OnInvalidSyntax.class).orElse(OnInvalidSyntax.FAIL);
        var rProtectedNamespaces = runContext.render(this.protectedNamespaces).asList(String.class);
        var rCommitMessage = runContext.render(this.getCommitMessage()).as(String.class).orElse("Namespace sync from Kestra");

        var git = gitService.cloneBranch(runContext, rBranch, this.cloneSubmodules);

        var repoWorktree = git.getRepository().getWorkTree().toPath();
        var baseDir = (rGitDirectory == null || rGitDirectory.isBlank()) ? repoWorktree : repoWorktree.resolve(rGitDirectory);

        // Read Git content (namespace-first) then filter to target namespace (no recursion)
        GitTree gitFlowsAll = readGitTreeNamespaceFirst(baseDir);
        GitTree gitFlows = filterTreeByNamespace(gitFlowsAll, rNamespace);

        // Read Git namespace files (<namespace>/files/**)
        Map<String, byte[]> gitFiles = readGitNamespaceFiles(baseDir, rNamespace);

        if (rSourceOfTruth == SourceOfTruth.KESTRA) {
            ensureNamespaceFolders(baseDir, rNamespace);
        }

        // Fetch Kestra state limited to target namespace only
        KestraState kestraState = loadKestraState(runContext, rNamespace);
        Map<String, byte[]> namespaceFiles = listNamespaceFiles(runContext, rNamespace);

        List<DiffLine> diffs = new ArrayList<>();
        List<Runnable> apply = new ArrayList<>();

        planFlows(runContext, baseDir, gitFlows, kestraState, rSourceOfTruth, rWhenMissingInSource, rOnInvalidSyntax, rProtectedNamespaces, rDryRun, diffs, apply);
        planNamespaceFiles(runContext, baseDir, rNamespace, gitFiles, namespaceFiles, rSourceOfTruth, rWhenMissingInSource, rProtectedNamespaces, rDryRun, diffs, apply);

        if (!rDryRun) {
            for (Runnable r : apply) r.run();
        }

        String addPattern = (rGitDirectory == null || rGitDirectory.isBlank()) ? "." : rGitDirectory;
        git.add().addFilepattern(addPattern).call();
        AddCommand update = git.add();
        update.setUpdate(true).addFilepattern(addPattern).call();

        String commitId = null;
        String commitURL = null;
        URI diffFile = null;

        if (!rDryRun) {
            try {
                diffFile = createIonDiff(runContext, git);

                PersonIdent author = author(runContext);
                git.commit().setAllowEmpty(false).setMessage(rCommitMessage).setAuthor(author).call();

                Iterable<PushResult> results = this.authentified(git.push(), runContext).call();
                for (PushResult pr : results) {
                    Optional<RemoteRefUpdate.Status> rejection = pr.getRemoteUpdates().stream()
                        .map(RemoteRefUpdate::getStatus)
                        .filter(Arrays.asList(REJECTED_NONFASTFORWARD, REJECTED_NODELETE, REJECTED_REMOTE_CHANGED, REJECTED_OTHER_REASON)::contains)
                        .findFirst();
                    if (rejection.isPresent()) {
                        throw new KestraRuntimeException(pr.getMessages());
                    }
                }

                ObjectId commit = git.getRepository().resolve(Constants.HEAD);
                commitId = commit != null ? commit.getName() : null;
                String httpUrl = gitService.getHttpUrl(runContext.render(this.url).as(String.class).orElse(null));
                commitURL = buildCommitUrl(httpUrl, rBranch, commitId);
            } catch (EmptyCommitException e) {
                runContext.logger().info("No changes to commit.");
            }
        } else {
            diffFile = DiffLine.writeIonFile(runContext, diffs);
        }

        return Output.builder()
            .diff(diffFile)
            .commitId(commitId)
            .commitURL(commitURL)
            .build();
    }

    private void planFlows(RunContext rc, Path baseDir, GitTree gitTree, KestraState kes,
                           SourceOfTruth rSource, WhenMissingInSource rMissing, OnInvalidSyntax rInvalid,
                           List<String> protectedNamespace, boolean rDryRun,
                           List<DiffLine> diff, List<Runnable> apply) {
        FlowService fs = flowService(rc);
        Map<String, GitNode> gitByKey = gitTree.nodes;
        Map<String, FlowWithSource> kesByKey = kes.flows;
        String tenant = rc.flowInfo().tenantId();

        Set<String> keys = union(gitByKey.keySet(), kesByKey.keySet());
        for (String key : keys) {
            GitNode gitNode = gitByKey.get(key);
            FlowWithSource kestraFlowWithSource = kesByKey.get(key);
            String namespace = namespaceFromKey(key);
            String fileRel = nodeToYamlPath(FLOWS_DIR, gitNode, key);

            if (gitNode != null && kestraFlowWithSource == null) {
                if (rSource == SourceOfTruth.GIT) {
                    diff.add(DiffLine.added(fileRel, key, Kind.FLOW));
                    if (!rDryRun) apply.add(() -> {
                        try {
                            fs.importFlow(tenant, gitNode.rawYaml, false);
                        } catch (FlowProcessingException e) {
                            handleInvalid(rc, rInvalid, "FLOW " + key, e);
                        }
                    });
                } else {
                    switch (rMissing) {
                        case KEEP -> diff.add(DiffLine.unchanged(fileRel, key, Kind.FLOW));
                        case DELETE -> {
                            if (isProtected(namespace, protectedNamespace)) {
                                rc.logger().warn("Protected namespace, skipping delete in Git for FLOW {}", key);
                                diff.add(DiffLine.unchanged(fileRel, key, Kind.FLOW));
                            } else {
                                diff.add(DiffLine.deletedGit(fileRel, key, Kind.FLOW));
                                if (!rDryRun) apply.add(() -> deleteGitFile(baseDir, fileRel));
                            }
                        }
                        case FAIL ->
                            throw new KestraRuntimeException("Sync failed: FLOW missing in KESTRA but present in Git for key " + key);
                    }
                }
            } else if (gitNode == null && kestraFlowWithSource != null) {
                if (rSource == SourceOfTruth.KESTRA) {
                    String rel = fileRelFromKey(FLOWS_DIR, key);
                    diff.add(DiffLine.added(rel, key, Kind.FLOW));
                    if (!rDryRun) apply.add(() -> {
                        ensureNamespaceFolders(baseDir, namespace);
                        writeGitFile(baseDir, rel, kestraFlowWithSource.getSource());
                    });
                } else {
                    switch (rMissing) {
                        case KEEP -> diff.add(DiffLine.unchanged(fileRelFromKey(FLOWS_DIR, key), key, Kind.FLOW));
                        case DELETE -> {
                            if (isProtected(namespace, protectedNamespace)) {
                                rc.logger().warn("Protected namespace, skipping delete for FLOW {}", key);
                                break;
                            }
                            diff.add(DiffLine.deletedKestra(fileRelFromKey(FLOWS_DIR, key), key, Kind.FLOW));
                            if (!rDryRun) apply.add(() -> deleteFlow(rc, kestraFlowWithSource));
                        }
                        case FAIL ->
                            throw new KestraRuntimeException("Sync failed: FLOW missing in Git but present in KESTRA for key " + key);
                    }
                }
            } else if (gitNode != null) {
                boolean changed = !Objects.equals(normalizeYaml(gitNode.rawYaml), normalizeYaml(kestraFlowWithSource.getSource()));
                if (!changed) {
                    diff.add(DiffLine.unchanged(fileRel, key, Kind.FLOW));
                    continue;
                }
                if (rSource == SourceOfTruth.GIT) {
                    diff.add(DiffLine.updatedKestra(fileRel, key, Kind.FLOW));
                    if (!rDryRun) apply.add(() -> {
                        try {
                            fs.importFlow(tenant, gitNode.rawYaml, false);
                        } catch (FlowProcessingException e) {
                            handleInvalid(rc, rInvalid, "FLOW " + key, e);
                        }
                    });
                } else {
                    diff.add(DiffLine.updatedGit(fileRel, key, Kind.FLOW));
                    if (!rDryRun) apply.add(() -> writeGitFile(baseDir, fileRel, kestraFlowWithSource.getSource()));
                }
            }
        }
    }

    private void planNamespaceFiles(RunContext rc, Path baseDir, String namespace, Map<String, byte[]> gitFiles,
                                    Map<String, byte[]> kestraFiles, SourceOfTruth rSource, WhenMissingInSource rMissing,
                                    List<String> protectedNamespace, boolean rDryRun, List<DiffLine> diff, List<Runnable> apply) {

        Set<String> paths = union(gitFiles.keySet(), kestraFiles.keySet());
        for (String rel : paths) {
            boolean inGit = gitFiles.containsKey(rel);
            boolean inKestra = kestraFiles.containsKey(rel);
            String fileRel = namespace + "/" + FILES_DIR + "/" + rel;

            if (inGit && !inKestra) {
                if (rSource == SourceOfTruth.GIT) {
                    diff.add(DiffLine.added(fileRel, namespace + ":" + rel, Kind.FILE));
                    if (!rDryRun) apply.add(() -> {
                        putNamespaceFile(rc, namespace, rel, gitFiles.get(rel));
                    });
                } else {
                    switch (rMissing) {
                        case KEEP -> diff.add(DiffLine.unchanged(fileRel, namespace + ":" + rel, Kind.FILE));
                        case DELETE -> {
                            if (isProtected(namespace, protectedNamespace)) {
                                rc.logger().warn("Protected namespace, skipping delete in Git for FILE {}:{}", namespace, rel);
                                diff.add(DiffLine.unchanged(fileRel, namespace + ":" + rel, Kind.FILE));
                            } else {
                                diff.add(DiffLine.deletedGit(fileRel, namespace + ":" + rel, Kind.FILE));
                                if (!rDryRun) apply.add(() -> deleteGitFile(baseDir, fileRel));
                            }
                        }
                        case FAIL ->
                            throw new KestraRuntimeException("Sync failed: FILE missing in KESTRA but present in Git: " + namespace + ":" + rel);
                    }
                }
                continue;
            }

            if (!inGit && inKestra) {
                if (rSource == SourceOfTruth.KESTRA) {
                    diff.add(DiffLine.added(fileRel, namespace + ":" + rel, Kind.FILE));
                    if (!rDryRun) apply.add(() -> writeGitBinaryFile(baseDir, fileRel, kestraFiles.get(rel)));
                } else {
                    switch (rMissing) {
                        case KEEP -> diff.add(DiffLine.unchanged(fileRel, namespace + ":" + rel, Kind.FILE));
                        case DELETE -> {
                            if (isProtected(namespace, protectedNamespace)) {
                                rc.logger().warn("Protected namespace, skipping delete for FILE {}:{}", namespace, rel);
                                break;
                            }
                            diff.add(DiffLine.deletedKestra(fileRel, namespace + ":" + rel, Kind.FILE));
                            if (!rDryRun) apply.add(() -> deleteNamespaceFile(rc, namespace, rel));
                        }
                        case FAIL ->
                            throw new KestraRuntimeException("Sync failed: FILE missing in Git but present in KESTRA: " + namespace + ":" + rel);
                    }
                }
                continue;
            }

            if (inGit) {
                byte[] gitFile = gitFiles.get(rel);
                byte[] kestraFile = kestraFiles.get(rel);
                if (Arrays.equals(gitFile, kestraFile)) {
                    diff.add(DiffLine.unchanged(fileRel, namespace + ":" + rel, Kind.FILE));
                } else if (rSource == SourceOfTruth.GIT) {
                    diff.add(DiffLine.updatedKestra(fileRel, namespace + ":" + rel, Kind.FILE));
                    if (!rDryRun) apply.add(() -> putNamespaceFile(rc, namespace, rel, gitFile));
                } else {
                    diff.add(DiffLine.updatedGit(fileRel, namespace + ":" + rel, Kind.FILE));
                    if (!rDryRun) apply.add(() -> writeGitBinaryFile(baseDir, fileRel, kestraFile));
                }
            }
        }
    }

    private record GitNode(String namespace, String id, String rawYaml, String relPath) {
    }

    private static class GitTree {
        Map<String, GitNode> nodes = new HashMap<>();
    }

    private record KestraState(Map<String, FlowWithSource> flows) {
    }

    private KestraState loadKestraState(RunContext rc, String rootNamespace) {
        FlowService fs = flowService(rc);
        String tenant = rc.flowInfo().tenantId();

        Map<String, FlowWithSource> flowsWithSource = fs.findByNamespaceWithSource(tenant, rootNamespace).stream()
            .collect(Collectors.toMap(f -> key(f.getNamespace(), f.getId()), Function.identity(), (a, b) -> a));

        return new KestraState(flowsWithSource);
    }

    private GitTree readGitTreeNamespaceFirst(Path baseDir) throws IOException {
        GitTree tree = new GitTree();
        if (baseDir == null || !Files.exists(baseDir)) return tree;

        Pattern p = Pattern.compile("(.+?)/" + Pattern.quote(FLOWS_DIR) + "/([^/]+)\\.(ya?ml|yml)$");

        try (Stream<Path> paths = Files.walk(baseDir, MAX_VALUE)) {
            paths.filter(Files::isRegularFile)
                .filter(YamlParser::isValidExtension)
                .forEach(throwConsumer(abs -> {
                    Path relPath = baseDir.relativize(abs);
                    String unix = relPath.toString().replace(File.separatorChar, '/');
                    Matcher m = p.matcher(unix);
                    if (!m.matches()) return;
                    String namespacePath = m.group(1);
                    String id = stripExt(new File(m.group(2)).getName());
                    String namespace = namespacePath.replace('/', '.');
                    String yaml = Files.readString(abs, StandardCharsets.UTF_8);
                    String compositeKey = key(namespace, id);
                    tree.nodes.put(compositeKey, new GitNode(namespace, id, yaml, unix));
                }));
        }
        return tree;
    }

    private Map<String, byte[]> readGitNamespaceFiles(Path baseDir, String namespace) throws IOException {
        Map<String, byte[]> out = new HashMap<>();
        if (baseDir == null || !Files.exists(baseDir)) return out;
        Path namespaceFilesRoot = baseDir.resolve(namespace).resolve(FILES_DIR);
        if (!Files.exists(namespaceFilesRoot)) return out;

        try (Stream<Path> paths = Files.walk(namespaceFilesRoot, MAX_VALUE)) {
            paths.filter(Files::isRegularFile)
                .forEach(throwConsumer(p -> {
                    Path rel = namespaceFilesRoot.relativize(p);
                    byte[] content = Files.readAllBytes(p);
                    String relUnix = rel.toString().replace(File.separatorChar, '/');
                    out.put(relUnix, content);
                }));
        }
        return out;
    }

    private Map<String, byte[]> listNamespaceFiles(RunContext runContext, String namespace) {
        try {
            var entries = runContext.storage().namespace(namespace).all(true);

            List<String> normalized = entries.stream()
                .map(NamespaceFile::path)
                .map(this::normalizeNamespacePath)
                .toList();

            Set<String> directories = new HashSet<>();
            for (String p : normalized) {
                String prefix = p + "/";
                for (String q : normalized) {
                    if (!p.equals(q) && q.startsWith(prefix)) {
                        directories.add(p);
                        break;
                    }
                }
            }

            Map<String, byte[]> out = new HashMap<>();
            for (int i = 0; i < entries.size(); i++) {
                String key = normalized.get(i);
                if (directories.contains(key)) continue;
                byte[] bytes = readNamespaceFileBytes(runContext, entries.get(i).uri());
                out.put(key, bytes);
            }

            return out;
        } catch (Exception e) {
            throw new KestraRuntimeException("Unable to list namespace files for " + namespace + ": " + e.getMessage(), e);
        }
    }

    private String normalizeNamespacePath(String path) {
        String p = path.replace("\\", "/");
        if (p.startsWith(FILES_DIR + "/")) {
            return p.substring(FILES_DIR.length() + 1);
        }
        return p;
    }

    private byte[] readNamespaceFileBytes(RunContext runContext, URI uri) {
        try (InputStream in = runContext.storage().getFile(uri)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new KestraRuntimeException(e);
        }
    }

    private void putNamespaceFile(RunContext runContext, String namespace, String rel, byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            runContext.storage().namespace(namespace).putFile(Path.of(rel), in);
        } catch (IOException | URISyntaxException e) {
            throw new KestraRuntimeException(e);
        }
    }

    private void deleteNamespaceFile(RunContext runContext, String namespace, String file) {
        try {
            var namespaceStore = runContext.storage().namespace(namespace);
            Path storagePath = NamespaceFile.of(namespace, Path.of(file)).storagePath();

            try {
                namespaceStore.delete(storagePath);
            } catch (IOException e1) {
                try {
                    namespaceStore.delete(Path.of("files").resolve(file));
                } catch (IOException e2) {
                    e2.addSuppressed(e1);
                    throw e2;
                }
            }
        } catch (IOException e) {
            throw new KestraRuntimeException(e);
        }
    }

    private GitTree filterTreeByNamespace(GitTree src, String rootNamespace) {
        GitTree out = new GitTree();
        src.nodes.forEach((k, v) -> {
            if (v.namespace.equals(rootNamespace)) {
                out.nodes.put(k, v);
            }
        });
        return out;
    }

    private void ensureNamespaceFolders(Path baseDir, String namespace) {
        if (baseDir == null) return;
        Path namespaceRoot = baseDir.resolve(namespace);
        for (String d : List.of(FLOWS_DIR, FILES_DIR)) {
            try {
                Files.createDirectories(namespaceRoot.resolve(d));
            } catch (IOException ignored) {
            }
        }
    }

    private void deleteFlow(RunContext rc, FlowWithSource flow) {
        flowService(rc).delete(flow);
    }

    private static boolean isProtected(String namespace, List<String> protectedNamespace) {
        if (namespace == null) return false;
        return protectedNamespace.stream().anyMatch(p -> p.equals(namespace) || namespace.startsWith(p + "."));
    }

    private static String normalizeYaml(String yaml) {
        return yaml == null ? null : yaml.replace("\r\n", "\n").trim();
    }

    private static String key(String namespace, String id) {
        return (namespace == null || namespace.isEmpty()) ? id : namespace + ":" + id;
    }

    private static String namespaceFromKey(String key) {
        int idx = key.indexOf(':');
        return idx < 0 ? "" : key.substring(0, idx);
    }

    private static String idFromKey(String key) {
        int idx = key.indexOf(':');
        return idx < 0 ? key : key.substring(idx + 1);
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    private static <T> Set<T> union(Set<T> a, Set<T> b) {
        Set<T> s = new HashSet<>(a);
        s.addAll(b);
        return s;
    }

    private static String fileRelFromKey(String kind, String key) {
        String namespace = namespaceFromKey(key);
        String id = idFromKey(key);
        Path rel = Path.of(namespace).resolve(kind).resolve(id + ".yaml");
        return rel.toString().replace(File.separatorChar, '/');
    }

    private static String nodeToYamlPath(String kind, GitNode g, String key) {
        if (g != null && g.relPath != null) return g.relPath;
        return fileRelFromKey(kind, key);
    }

    private void writeGitFile(Path base, String rel, String content) {
        try {
            Path target = base.resolve(rel);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new KestraRuntimeException(e);
        }
    }

    private void writeGitBinaryFile(Path base, String rel, byte[] content) {
        try {
            Path target = base.resolve(rel);
            Files.createDirectories(target.getParent());
            Files.write(target, content == null ? new byte[0] : content);
        } catch (IOException e) {
            throw new KestraRuntimeException(e);
        }
    }

    private void deleteGitFile(Path base, String rel) {
        try {
            Path target = base.resolve(rel);
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new KestraRuntimeException(e);
        }
    }

    private void handleInvalid(RunContext rc, OnInvalidSyntax mode, String what, Exception e) {
        switch (mode) {
            case SKIP -> rc.logger().info("Skipping invalid {}", what);
            case WARN -> rc.logger().warn("{} couldn't be synced due to invalid syntax: {}", what, e.getMessage());
            case FAIL -> throw new KestraRuntimeException("Invalid syntax for " + what + ": " + e.getMessage(), e);
        }
    }

    private PersonIdent author(RunContext runContext) throws IllegalVariableEvaluationException {
        String rName = runContext.render(this.authorName).as(String.class).orElse(runContext.render(this.username).as(String.class).orElse(null));
        String rEmail = runContext.render(this.authorEmail).as(String.class).orElse(null);
        if (rEmail == null || rName == null) return null;
        return new PersonIdent(rName, rEmail);
    }

    private Property<String> getCommitMessage() {
        return Optional.ofNullable(this.commitMessage).orElse(Property.ofValue("Namespace sync from Kestra"));
    }

    @SuperBuilder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "A file containing all changes applied (or not in case of dry run) to/from Git.")
        private URI diff;

        @Schema(title = "ID of the commit pushed (if any).")
        @Nullable
        private String commitId;

        @Schema(title = "URL to the commit (if any).")
        @Nullable
        private String commitURL;
    }
}
