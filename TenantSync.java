package io.kestra.plugin.git;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.git.services.GitService;
import io.kestra.sdk.KestraClient;
import io.kestra.sdk.api.FilesApi;
import io.kestra.sdk.internal.ApiException;
import io.kestra.sdk.model.FileAttributes;
import io.kestra.sdk.model.Namespace;
import io.kestra.sdk.model.PagedResultsDashboard;
import io.kestra.sdk.model.PagedResultsNamespace;
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

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.eclipse.jgit.transport.RemoteRefUpdate.Status.*;

@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Unidirectional tenant sync between Kestra and Git.",
    description = "Synchronizes ALL namespaces, flows, files, and dashboards between Kestra and Git."
)
@Plugin(
    priority = Plugin.Priority.SECONDARY,
    examples = {
        @Example(
            title = "Sync all objects (flows, files, dashboards, namespaces) under the same tenant than this flow using Git as source of truth",
            full = true,
            code = """
                id: tenant_sync_git
                namespace: system
                tasks:
                  - id: sync
                    type: io.kestra.plugin.git.TenantSync
                    sourceOfTruth: GIT
                    whenMissingInSource: DELETE
                    protectedNamespaces:
                      - system
                    url: https://github.com/fdelbrayelle/plugin-git-qa
                    username: fdelbrayelle
                    password: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                    branch: main
                    gitDirectory: kestra
                    kestraUrl: "http://localhost:8080"
                    auth:
                      username: "{{ secret('KESTRA_USERNAME') }}"
                      password: "{{ secret('KESTRA_PASSWORD') }}"
                """
        ),
        @Example(
            title = "Sync all objects (flows, files, dashboards, namespaces) under the same tenant as this flow using Kestra as the source of truth",
            full = true,
            code = """
                id: tenant_sync_kestra
                namespace: system
                tasks:
                  - id: sync
                    type: io.kestra.plugin.git.TenantSync
                    sourceOfTruth: KESTRA
                    whenMissingInSource: KEEP
                    url: https://github.com/fdelbrayelle/plugin-git-qa
                    username: fdelbrayelle
                    password: "{{ secret('GITHUB_ACCESS_TOKEN') }}"
                    branch: dev
                    kestraUrl: "http://localhost:8080"
                    auth:
                      username: "{{ secret('KESTRA_USERNAME') }}"
                      password: "{{ secret('KESTRA_PASSWORD') }}"
                """
        )
    }
)
public class TenantSync extends AbstractKestraTask implements RunnableTask<TenantSync.Output> {

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
                | projectA     | company.team    | projectA/company.team/flows/my-flow.yaml |
            """
    )
    private Property<String> gitDirectory;

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

    @Schema(title = "Whether to clone submodules")
    protected Property<Boolean> cloneSubmodules;

    // Directory names
    private static final String FLOWS_DIR = "flows";
    private static final String FILES_DIR = "files";
    private static final String DASHBOARDS_DIR = "_global/dashboards";

    @Override
    public Output run(RunContext runContext) throws Exception {

        runContext.logger().info("Now in TenantSync for tenant {}", runContext.flowInfo().tenantId());

        GitService gitService = new GitService(this);
        KestraClient kestraClient = kestraClient(runContext);

        String tenantId = runContext.flowInfo().tenantId();
        String rBranch = runContext.render(this.branch).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Branch must be explicitly set."));
        String rGitDirectory = runContext.render(this.gitDirectory).as(String.class).orElse(null);
        SourceOfTruth rSourceOfTruth = runContext.render(this.sourceOfTruth).as(SourceOfTruth.class)
            .orElse(SourceOfTruth.KESTRA);
        WhenMissingInSource rWhenMissingInSource = runContext.render(this.whenMissingInSource)
            .as(WhenMissingInSource.class).orElse(WhenMissingInSource.DELETE);
        boolean rDryRun = runContext.render(this.dryRun).as(Boolean.class).orElse(false);
        OnInvalidSyntax rOnInvalidSyntax = runContext.render(this.onInvalidSyntax)
            .as(OnInvalidSyntax.class).orElse(OnInvalidSyntax.FAIL);
        List<String> rProtectedNamespaces = runContext.render(this.protectedNamespaces).asList(String.class);
        String rCommitMessage = runContext.render(this.getCommitMessage())
            .as(String.class).orElse("Tenant sync from Kestra");

        var git = gitService.cloneBranch(runContext, rBranch, this.cloneSubmodules);
        Path repoWorktree = git.getRepository().getWorkTree().toPath();
        Path baseDir = (rGitDirectory == null || rGitDirectory.isBlank())
            ? repoWorktree
            : repoWorktree.resolve(rGitDirectory);

        List<String> kestraNamespaces = new ArrayList<>();
        int page = 1;
        int size = 200;
        PagedResultsNamespace result;
        do {
            result = kestraClient.namespaces()
                .searchNamespaces(page, size, false, tenantId, null, null, Map.of());
            result.getResults().forEach(ns -> kestraNamespaces.add(ns.getId()));
            page++;
        } while (result.getResults().size() == size);

        List<DiffLine> diffs = new ArrayList<>();
        List<Runnable> apply = new ArrayList<>();

        Set<String> namespaces = new LinkedHashSet<>(kestraNamespaces);

        if (rSourceOfTruth == SourceOfTruth.GIT) {
            namespaces.addAll(discoverGitNamespaces(baseDir));
        }

        for (String namespace : namespaces) {
            runContext.logger().info("Processing namespace {}", namespace);
            if (rSourceOfTruth == SourceOfTruth.GIT && !rDryRun && !kestraNamespaces.contains(namespace)) {
                Path namespaceRoot = baseDir.resolve(namespace);
                boolean gitHasContent = java.nio.file.Files.isDirectory(namespaceRoot.resolve(FLOWS_DIR)) || java.nio.file.Files.isDirectory(namespaceRoot.resolve(FILES_DIR));

                if (gitHasContent) {
                    try {
                        kestraClient.namespaces().createNamespace(tenantId, new Namespace().id(namespace));
                        kestraNamespaces.add(namespace);
                    } catch (Exception ignored) {
                    }
                }
            }

            List<FlowWithSource> kestraFlows = fetchFlowsFromKestra(kestraClient, runContext, namespace);
            Map<String, byte[]> kestraFiles = listNamespaceFiles(kestraClient, runContext, namespace);

            planNamespace(
                runContext, kestraClient, baseDir, namespace,
                rSourceOfTruth, rWhenMissingInSource,
                rOnInvalidSyntax, rProtectedNamespaces,
                rDryRun, diffs, apply,
                kestraFlows, kestraFiles
            );
        }

        planDashboards(
            runContext, kestraClient, baseDir,
            rSourceOfTruth, rWhenMissingInSource,
            rOnInvalidSyntax, rDryRun, diffs, apply
        );

        String addPattern = (rGitDirectory == null || rGitDirectory.isBlank()) ? "." : rGitDirectory;

        String rCommitId = null;
        String rCommitURL = null;
        URI diffFile;

        if (!rDryRun) {
            for (Runnable r : apply) r.run();

            git.add().addFilepattern(addPattern).call();
            AddCommand update = git.add();
            update.setUpdate(true).addFilepattern(addPattern).call();

            diffFile = createIonDiff(runContext, git);

            try {
                PersonIdent author = author(runContext);
                git.commit().setAllowEmpty(false).setMessage(rCommitMessage).setAuthor(author).call();

                Iterable<PushResult> results = this.authentified(git.push(), runContext).call();
                for (PushResult pr : results) {
                    Optional<RemoteRefUpdate.Status> rejection = pr.getRemoteUpdates().stream()
                        .map(RemoteRefUpdate::getStatus)
                        .filter(Arrays.asList(
                            REJECTED_NONFASTFORWARD,
                            REJECTED_NODELETE,
                            REJECTED_REMOTE_CHANGED,
                            REJECTED_OTHER_REASON
                        )::contains)
                        .findFirst();
                    if (rejection.isPresent()) {
                        throw new KestraRuntimeException(pr.getMessages());
                    }
                }

                ObjectId commit = git.getRepository().resolve(Constants.HEAD);
                rCommitId = commit != null ? commit.getName() : null;
                String httpUrl = gitService.getHttpUrl(runContext.render(this.url).as(String.class).orElse(null));
                rCommitURL = buildCommitUrl(httpUrl, rBranch, rCommitId);

            } catch (EmptyCommitException e) {
                runContext.logger().info("No changes to commit.");
            }
        } else {
            diffFile = DiffLine.writeIonFile(runContext, diffs);
        }

        git.close();

        return Output.builder()
            .diff(diffFile)
            .commitId(rCommitId)
            .commitURL(rCommitURL)
            .build();
    }

    private void planNamespace(
        RunContext runContext,
        KestraClient kestraClient,
        Path baseDir,
        String namespace,
        SourceOfTruth rSourceOfTruth,
        WhenMissingInSource rWhenMissingInSource,
        OnInvalidSyntax rOnInvalidSyntax,
        List<String> rProtectedNamespaces,
        boolean rDryRun,
        List<DiffLine> diffs,
        List<Runnable> apply,
        List<FlowWithSource> kestraFlows,
        Map<String, byte[]> kestraFiles
    ) throws Exception {

        Path namespaceRoot = baseDir.resolve(namespace);
        Path flowsDir = namespaceRoot.resolve(FLOWS_DIR);
        Path filesDir = namespaceRoot.resolve(FILES_DIR);

        var gitFlows = readGitFlows(flowsDir);
        var gitFiles = readGitFiles(filesDir);

        planFlows(runContext, flowsDir, gitFlows, kestraFlows, namespace, rSourceOfTruth,
            rWhenMissingInSource, rOnInvalidSyntax, rProtectedNamespaces, rDryRun, diffs, apply);

        planNamespaceFiles(runContext, kestraClient, filesDir, gitFiles, kestraFiles, namespace, rSourceOfTruth,
            rWhenMissingInSource, rProtectedNamespaces, rDryRun, diffs, apply);
    }

    private Map<String, byte[]> listNamespaceFiles(KestraClient kestraClient, RunContext runContext, String namespace) {
        try {
            var filesApi = kestraClient.files();
            String tenantId = runContext.flowInfo().tenantId();

            Map<String, byte[]> files = new HashMap<>();
            collectFilesRecursive(filesApi, tenantId, namespace, null, files);

            return files;
        } catch (Exception e) {
            throw new KestraRuntimeException("Unable to list namespace files for " + namespace + ": " + e.getMessage(), e);
        }
    }

    private void collectFilesRecursive(
        FilesApi filesApi,
        String tenantId,
        String namespace,
        @Nullable String currentPath,
        Map<String, byte[]> filesOut
    ) throws IOException, ApiException {
        List<FileAttributes> children = filesApi.listNamespaceDirectoryFiles(namespace, tenantId, currentPath);

        for (FileAttributes child : children) {
            String name = child.getFileName();
            if (name == null) continue; // skip malformed

            String fullPath = currentPath == null ? name : currentPath + "/" + name;

            if ("directory".equalsIgnoreCase(String.valueOf(child.getType()))) {
                collectFilesRecursive(filesApi, tenantId, namespace, fullPath, filesOut);
            } else if ("file".equalsIgnoreCase(String.valueOf(child.getType()))) {
                File file = filesApi.getFileContent(namespace, fullPath, tenantId);
                try (InputStream is = new FileInputStream(file)) {
                    filesOut.put(normalizeNamespacePath(fullPath), is.readAllBytes());
                }
            }
        }
    }

    private String normalizeNamespacePath(String path) {
        return path.replace("\\", "/");
    }

    private void planFlows(
        RunContext runContext,
        Path flowsDir,
        Map<String, String> gitFlows,
        List<FlowWithSource> kestraFlows,
        String namespace,
        SourceOfTruth rSourceOfTruth,
        WhenMissingInSource rWhenMissingInSource,
        OnInvalidSyntax rOnInvalidSyntax,
        List<String> rProtectedNamespaces,
        boolean rDryRun,
        List<DiffLine> diffs,
        List<Runnable> apply
    ) {
        Map<String, String> kestraFlowsMap = kestraFlows.stream()
            .collect(Collectors.toMap(FlowWithSource::getId, FlowWithSource::getSource));

        Set<String> allFlowIds = new HashSet<>();
        allFlowIds.addAll(gitFlows.keySet());
        allFlowIds.addAll(kestraFlowsMap.keySet());

        for (String flowId : allFlowIds) {
            Path flowPath = flowsDir.resolve(flowId + ".yaml");
            String gitYaml = gitFlows.get(flowId);
            String kestraYaml = kestraFlowsMap.get(flowId);

            // Case 1: Flow exists only in Git
            String tenantId = runContext.flowInfo().tenantId();
            if (gitYaml != null && kestraYaml == null) {
                if (rSourceOfTruth == SourceOfTruth.GIT) {
                    diffs.add(DiffLine.added(flowPath.toString(), flowId, Kind.FLOW));
                    if (!rDryRun) {
                        apply.add(() -> {
                            try {
                                kestraClient(runContext).flows().importFlows(
                                    runContext.flowInfo().tenantId(),
                                    toNamedTempFile(flowId + ".yaml", gitYaml),
                                    Map.of()
                                );
                            } catch (Exception e) {
                                handleInvalid(runContext, rOnInvalidSyntax, "FLOW " + flowId, e);
                            }
                        });
                    }
                } else {
                    switch (rWhenMissingInSource) {
                        case KEEP -> diffs.add(DiffLine.unchanged(flowPath.toString(), flowId, Kind.FLOW));
                        case DELETE -> {
                            if (isProtected(namespace, rProtectedNamespaces)) {
                                runContext.logger().warn("Protected namespace, skipping delete in Git for FLOW {}", flowId);
                            } else {
                                diffs.add(DiffLine.deletedGit(flowPath.toString(), flowId, Kind.FLOW));
                                if (!rDryRun) apply.add(() -> deleteGitFile(flowPath));
                            }
                        }
                        case FAIL -> throw new KestraRuntimeException(
                            "Sync failed: FLOW " + flowId + " missing in Kestra but present in Git"
                        );
                    }
                }

                // Case 2: Flow exists only in Kestra
            } else if (gitYaml == null && kestraYaml != null) {
                if (rSourceOfTruth == SourceOfTruth.KESTRA) {
                    diffs.add(DiffLine.added(flowPath.toString(), flowId, Kind.FLOW));
                    if (!rDryRun) apply.add(() -> writeGitFile(flowPath, kestraYaml));
                } else {
                    switch (rWhenMissingInSource) {
                        case KEEP -> diffs.add(DiffLine.unchanged(flowPath.toString(), flowId, Kind.FLOW));
                        case DELETE -> {
                            if (isProtected(namespace, rProtectedNamespaces)) {
                                runContext.logger().warn("Protected namespace, skipping delete for FLOW {}", flowId);
                            } else {
                                diffs.add(DiffLine.deletedKestra(flowPath.toString(), flowId, Kind.FLOW));
                                if (!rDryRun) {
                                    apply.add(() -> {
                                        try {
                                            kestraClient(runContext).flows()
                                                .deleteFlow(namespace, flowId, tenantId);
                                        } catch (Exception e) {
                                            handleInvalid(runContext, rOnInvalidSyntax, "FLOW " + flowId, e);
                                        }
                                    });
                                }
                            }
                        }
                        case FAIL -> throw new KestraRuntimeException(
                            "Sync failed: FLOW " + flowId + " missing in Git but present in Kestra"
                        );
                    }
                }

                // Case 3: Flow exists in both Git and Kestra
            } else if (gitYaml != null) {
                boolean changed = !normalizeYaml(gitYaml).equals(normalizeYaml(kestraYaml));
                if (!changed) {
                    diffs.add(DiffLine.unchanged(flowPath.toString(), flowId, Kind.FLOW));
                    continue;
                }

                if (rSourceOfTruth == SourceOfTruth.GIT) {
                    diffs.add(DiffLine.updatedKestra(flowPath.toString(), flowId, Kind.FLOW));
                    if (!rDryRun) {
                        apply.add(() -> {
                            try {
                                kestraClient(runContext).flows().importFlows(
                                    runContext.flowInfo().tenantId(),
                                    toNamedTempFile(flowId + ".yaml", gitYaml),
                                    Map.of()
                                );
                            } catch (Exception e) {
                                handleInvalid(runContext, rOnInvalidSyntax, "FLOW " + flowId, e);
                            }
                        });
                    }
                } else {
                    diffs.add(DiffLine.updatedGit(flowPath.toString(), flowId, Kind.FLOW));
                    if (!rDryRun) apply.add(() -> writeGitFile(flowPath, kestraYaml));
                }
            }
        }
    }

    private void planNamespaceFiles(
        RunContext runContext,
        KestraClient kestraClient,
        Path filesDir,
        Map<String, byte[]> gitFiles,
        Map<String, byte[]> kestraFiles,
        String namespace,
        SourceOfTruth rSourceOfTruth,
        WhenMissingInSource rWhenMissingInSource,
        List<String> rProtectedNamespaces,
        boolean rDryRun,
        List<DiffLine> diffs,
        List<Runnable> apply
    ) {
        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(gitFiles.keySet());
        allFiles.addAll(kestraFiles.keySet());

        for (String rel : allFiles) {
            Path filePath = filesDir.resolve(rel);
            boolean inGit = gitFiles.containsKey(rel);
            boolean inKestra = kestraFiles.containsKey(rel);

            if (inGit && !inKestra) {
                if (rSourceOfTruth == SourceOfTruth.GIT) {
                    diffs.add(DiffLine.added(filePath.toString(), rel, Kind.FILE));
                    if (!rDryRun)
                        apply.add(() -> putNamespaceFile(kestraClient, runContext, rel, gitFiles.get(rel), namespace));
                } else {
                    switch (rWhenMissingInSource) {
                        case KEEP -> diffs.add(DiffLine.unchanged(filePath.toString(), rel, Kind.FILE));
                        case DELETE -> {
                            diffs.add(DiffLine.deletedGit(filePath.toString(), rel, Kind.FILE));
                            if (!rDryRun) apply.add(() -> deleteGitFile(filePath));
                        }
                        case FAIL -> throw new KestraRuntimeException(
                            "Sync failed: FILE missing in Kestra but present in Git: " + rel
                        );
                    }
                }
                continue;
            }

            if (!inGit && inKestra) {
                if (rSourceOfTruth == SourceOfTruth.KESTRA) {
                    diffs.add(DiffLine.added(filePath.toString(), rel, Kind.FILE));
                    if (!rDryRun) apply.add(() -> writeGitBinaryFile(filePath, kestraFiles.get(rel)));
                } else {
                    switch (rWhenMissingInSource) {
                        case KEEP -> diffs.add(DiffLine.unchanged(filePath.toString(), rel, Kind.FILE));
                        case DELETE -> {
                            if (isProtected(namespace, rProtectedNamespaces)) {
                                runContext.logger().warn("Protected namespace, skipping delete for FILE {}", rel);
                            } else {
                                diffs.add(DiffLine.deletedKestra(filePath.toString(), rel, Kind.FILE));
                                if (!rDryRun)
                                    apply.add(() -> deleteNamespaceFile(kestraClient, runContext, rel, namespace));
                            }
                        }
                        case FAIL -> throw new KestraRuntimeException(
                            "Sync failed: FILE missing in Git but present in Kestra: " + rel
                        );
                    }
                }
                continue;
            }

            if (inGit) {
                byte[] gitFile = gitFiles.get(rel);
                byte[] kestraFile = kestraFiles.get(rel);
                if (Arrays.equals(gitFile, kestraFile)) {
                    diffs.add(DiffLine.unchanged(filePath.toString(), rel, Kind.FILE));
                } else if (rSourceOfTruth == SourceOfTruth.GIT) {
                    diffs.add(DiffLine.updatedKestra(filePath.toString(), rel, Kind.FILE));
                    if (!rDryRun) apply.add(() -> putNamespaceFile(kestraClient, runContext, rel, gitFile, namespace));
                } else {
                    diffs.add(DiffLine.updatedGit(filePath.toString(), rel, Kind.FILE));
                    if (!rDryRun) apply.add(() -> writeGitBinaryFile(filePath, kestraFile));
                }
            }
        }
    }

    private void planDashboards(
        RunContext runContext,
        KestraClient kestraClient,
        Path baseDir,
        SourceOfTruth rSourceOfTruth,
        WhenMissingInSource rWhenMissingInSource,
        OnInvalidSyntax rOnInvalidSyntax,
        boolean rDryRun,
        List<DiffLine> diffs,
        List<Runnable> apply
    ) throws Exception {
        Path dashboardsDir = baseDir.resolve(DASHBOARDS_DIR);

        Map<String, String> kestraDashboards = fetchDashboardsFromKestra(kestraClient, runContext);

        Map<String, String> gitDashboards = readGitDashboards(dashboardsDir);

        Set<String> allDashboards = new HashSet<>();
        allDashboards.addAll(kestraDashboards.keySet());
        allDashboards.addAll(gitDashboards.keySet());

        for (String dashboardId : allDashboards) {
            Path dashboardPath = dashboardsDir.resolve(dashboardId + ".yaml");
            String gitYaml = gitDashboards.get(dashboardId);
            String kestraYaml = kestraDashboards.get(dashboardId);

            // Dashboard exists only in Git
            if (gitYaml != null && kestraYaml == null) {
                if (rSourceOfTruth == SourceOfTruth.GIT) {
                    diffs.add(DiffLine.added(dashboardPath.toString(), dashboardId, Kind.DASHBOARD));
                    if (!rDryRun) {
                        apply.add(() -> {
                            try {
                                kestraClient.dashboards().createDashboard(
                                    runContext.flowInfo().tenantId(),
                                    gitYaml
                                );
                            } catch (Exception e) {
                                handleInvalid(runContext, rOnInvalidSyntax, "DASHBOARD " + dashboardId, e);
                            }
                        });
                    }
                } else {
                    switch (rWhenMissingInSource) {
                        case KEEP ->
                            diffs.add(DiffLine.unchanged(dashboardPath.toString(), dashboardId, Kind.DASHBOARD));
                        case DELETE -> {
                            diffs.add(DiffLine.deletedGit(dashboardPath.toString(), dashboardId, Kind.DASHBOARD));
                            if (!rDryRun) apply.add(() -> deleteGitFile(dashboardPath));
                        }
                        case FAIL -> throw new KestraRuntimeException(
                            "Sync failed: DASHBOARD missing in Kestra but present in Git: " + dashboardId
                        );
                    }
                }

                // Dashboard exists only in Kestra
            } else if (gitYaml == null && kestraYaml != null) {
                if (rSourceOfTruth == SourceOfTruth.KESTRA) {
                    diffs.add(DiffLine.added(dashboardPath.toString(), dashboardId, Kind.DASHBOARD));
                    if (!rDryRun) apply.add(() -> writeGitFile(dashboardPath, kestraYaml));
                } else {
                    switch (rWhenMissingInSource) {
                        case KEEP ->
                            diffs.add(DiffLine.unchanged(dashboardPath.toString(), dashboardId, Kind.DASHBOARD));
                        case DELETE -> {
                            diffs.add(DiffLine.deletedKestra(dashboardPath.toString(), dashboardId, Kind.DASHBOARD));
                            if (!rDryRun) {
                                apply.add(() -> {
                                    try {
                                        kestraClient.dashboards().deleteDashboard(
                                            dashboardId, runContext.flowInfo().tenantId()
                                        );
                                    } catch (Exception e) {
                                        handleInvalid(runContext, rOnInvalidSyntax, "DASHBOARD " + dashboardId, e);
                                    }
                                });
                            }
                        }
                        case FAIL -> throw new KestraRuntimeException(
                            "Sync failed: DASHBOARD missing in Git but present in Kestra: " + dashboardId
                        );
                    }
                }

                // Dashboard exists in both Git and Kestra
            } else if (gitYaml != null) {
                boolean changed = !normalizeYaml(gitYaml).equals(normalizeYaml(kestraYaml));
                if (!changed) {
                    diffs.add(DiffLine.unchanged(dashboardPath.toString(), dashboardId, Kind.DASHBOARD));
                    continue;
                }

                if (rSourceOfTruth == SourceOfTruth.GIT) {
                    diffs.add(DiffLine.updatedKestra(dashboardPath.toString(), dashboardId, Kind.DASHBOARD));
                    if (!rDryRun) {
                        apply.add(() -> {
                            try {
                                kestraClient.dashboards().createDashboard(
                                    runContext.flowInfo().tenantId(),
                                    gitYaml
                                );
                            } catch (Exception e) {
                                handleInvalid(runContext, rOnInvalidSyntax, "DASHBOARD " + dashboardId, e);
                            }
                        });
                    }
                } else {
                    diffs.add(DiffLine.updatedGit(dashboardPath.toString(), dashboardId, Kind.DASHBOARD));
                    if (!rDryRun) apply.add(() -> writeGitFile(dashboardPath, kestraYaml));
                }
            }
        }
    }

    private List<FlowWithSource> fetchFlowsFromKestra(KestraClient kestraClient, RunContext runContext, String namespace) {
        try {
            // Export all flows from Kestra for the given namespace (including sub-namespaces)
            byte[] zippedFlows = kestraClient.flows().exportFlowsByQuery(
                runContext.flowInfo().tenantId(),
                null, // ids
                null,       // q
                null,       // sort
                namespace,  // filter by namespace (includes children)
                null        // other params
            );

            List<FlowWithSource> flows = new ArrayList<>();

            try (
                var bais = new ByteArrayInputStream(zippedFlows);
                var zis = new java.util.zip.ZipInputStream(bais)
            ) {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.getName().endsWith(".yml") && !entry.getName().endsWith(".yaml")) {
                        continue;
                    }

                    String yaml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);

                    io.kestra.core.models.flows.Flow parsed = io.kestra.core.serializers.YamlParser.parse(yaml, io.kestra.core.models.flows.Flow.class);

                    if (!namespace.equals(parsed.getNamespace())) {
                        continue;
                    }

                    flows.add(FlowWithSource.of(parsed, yaml));
                }
            }

            return flows;

        } catch (Exception e) {
            throw new KestraRuntimeException("Failed to export flows from Kestra for namespace " + namespace, e);
        }
    }

    private Map<String, String> fetchDashboardsFromKestra(KestraClient kestraClient, RunContext runContext) {
        try {
            Map<String, String> dashboards = new HashMap<>();

            int page = 1;
            int size = 200;
            PagedResultsDashboard pagedResults;

            do {
                pagedResults = kestraClient.dashboards().searchDashboards(page, size, runContext.flowInfo().tenantId(), null, null);

                pagedResults.getResults().forEach(dash -> {
                    dashboards.put(dash.getTitle(), dash.getSourceCode());
                });

                page++;
            } while (pagedResults.getResults().size() == size);

            return dashboards;

        } catch (Exception e) {
            throw new KestraRuntimeException("Failed to fetch dashboards from Kestra", e);
        }
    }

    private Map<String, String> readGitDashboards(Path dashboardsDir) throws IOException {
        Map<String, String> dashboards = new HashMap<>();
        if (!Files.exists(dashboardsDir)) {
            return dashboards;
        }

        try (var paths = Files.walk(dashboardsDir)) {
            paths.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                .forEach(p -> {
                    try {
                        String id = p.getFileName().toString().replaceFirst("\\.ya?ml$", "");
                        dashboards.put(id, Files.readString(p, StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to read dashboard from Git: " + p, e);
                    }
                });
        }
        return dashboards;
    }

    private Map<String, String> readGitFlows(Path flowsDir) throws IOException {
        Map<String, String> flows = new HashMap<>();
        if (!Files.exists(flowsDir)) {
            return flows;
        }

        try (var paths = Files.walk(flowsDir)) {
            paths.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                .forEach(p -> {
                    try {
                        String id = p.getFileName().toString().replaceFirst("\\.ya?ml$", "");
                        flows.put(id, Files.readString(p, StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to read flow from Git: " + p, e);
                    }
                });
        }
        return flows;
    }

    private Map<String, byte[]> readGitFiles(Path filesDir) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        if (!Files.exists(filesDir)) {
            return files;
        }

        try (var paths = Files.walk(filesDir)) {
            paths.filter(Files::isRegularFile)
                .forEach(p -> {
                    try {
                        Path rel = filesDir.relativize(p);
                        files.put(rel.toString().replace(File.separatorChar, '/'), Files.readAllBytes(p));
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to read file from Git: " + p, e);
                    }
                });
        }
        return files;
    }

    private void writeGitFile(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new KestraRuntimeException(e);
        }
    }

    private void writeGitBinaryFile(Path path, byte[] content) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, content);
        } catch (IOException e) {
            throw new KestraRuntimeException(e);
        }
    }

    private void deleteGitFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new KestraRuntimeException(e);
        }
    }

    private void putNamespaceFile(KestraClient kestraClient, RunContext runContext, String rel, byte[] bytes, String namespace) {
        try {
            File temp = File.createTempFile("tmp", null);
            Files.write(temp.toPath(), bytes);
            String tenant = runContext.flowInfo().tenantId();

            var filesApi = kestraClient.files();
            var path = Path.of(rel);

            String directory = path.getParent() != null ? path.getParent().toString().replace("\\", "/") : null;
            if (directory != null) {
                filesApi.createNamespaceDirectory(namespace, tenant, directory);
            }

            filesApi.createNamespaceFile(namespace, normalizeNamespacePath(rel), tenant, temp);
        } catch (Exception e) {
            throw new KestraRuntimeException("Failed to put namespace file: " + rel, e);
        }
    }

    private void deleteNamespaceFile(KestraClient kestraClient, RunContext runContext, String rel, String namespace) {
        try {
            var filesApi = kestraClient.files();
            filesApi.deleteFileDirectory(
                namespace,
                normalizeNamespacePath(rel),
                runContext.flowInfo().tenantId()
            );
        } catch (Exception e) {
            throw new KestraRuntimeException("Failed to delete namespace file: " + rel, e);
        }
    }

    private static boolean isProtected(String namespace, List<String> protectedNamespace) {
        if (namespace == null) return false;
        return protectedNamespace.stream().anyMatch(p -> p.equals(namespace) || namespace.startsWith(p + "."));
    }

    private static String normalizeYaml(String yaml) {
        return yaml == null ? null : yaml.replace("\r\n", "\n").trim();
    }

    private void handleInvalid(RunContext runContext, OnInvalidSyntax mode, String what, Exception e) {
        switch (mode) {
            case SKIP -> runContext.logger().info("Skipping invalid {}", what);
            case WARN ->
                runContext.logger().warn("{} couldn't be synced due to invalid syntax: {}", what, e.getMessage());
            case FAIL -> throw new KestraRuntimeException("Invalid syntax for " + what + ": " + e.getMessage(), e);
        }
    }

    private File toNamedTempFile(String fileName, String yaml) {
        try {
            Path tmpPath = Files.createTempDirectory("kestra-import")
                .resolve(fileName.endsWith(".yaml") ? fileName : fileName + ".yaml");

            Files.createDirectories(tmpPath.getParent());
            Files.writeString(tmpPath, yaml, StandardCharsets.UTF_8);

            return tmpPath.toFile();
        } catch (IOException e) {
            throw new KestraRuntimeException("Failed to create named file for: " + fileName, e);
        }
    }

    private PersonIdent author(RunContext runContext) throws IllegalVariableEvaluationException {
        String rName = runContext.render(this.authorName).as(String.class).orElse(runContext.render(this.username).as(String.class).orElse(null));
        String rEmail = runContext.render(this.authorEmail).as(String.class).orElse(null);
        if (rEmail == null || rName == null) return null;
        return new PersonIdent(rName, rEmail);
    }

    private Set<String> discoverGitNamespaces(Path baseDir) throws IOException {
        Set<String> out = new HashSet<>();
        if (!Files.exists(baseDir)) return out;

        try (var stream = Files.list(baseDir)) {
            for (Path namespaceDir : (Iterable<Path>) stream::iterator) {
                if (!Files.isDirectory(namespaceDir)) continue;
                boolean isNamespace = Files.isDirectory(namespaceDir.resolve(FLOWS_DIR)) || Files.isDirectory(namespaceDir.resolve(FILES_DIR));
                if (isNamespace) {
                    out.add(namespaceDir.getFileName().toString());
                }
            }
        }
        return out;
    }

    private Property<String> getCommitMessage() {
        return Optional.ofNullable(this.commitMessage).orElse(Property.ofValue("Tenant sync from Kestra"));
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
