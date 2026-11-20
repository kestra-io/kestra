package io.kestra.core.runners;

import java.io.*;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.URIFetcher;
import io.kestra.core.models.tasks.runners.PluginUtilsService;
import io.kestra.core.models.tasks.runners.ScriptService;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.utils.IdUtils;

import static io.kestra.core.utils.Rethrow.throwBiConsumer;

public abstract class FilesService {
    public static Map<String, String> inputFiles(RunContext runContext, Object inputs) throws Exception {
        return FilesService.inputFiles(runContext, Collections.emptyMap(), inputs);
    }

    public static Map<String, String> inputFiles(RunContext runContext, Map<String, Object> additionalVars, Object inputs) throws Exception {
        Logger logger = runContext.logger();

        Map<String, String> inputFiles = new HashMap<>(
            inputs == null ? Map.of()
                : PluginUtilsService.transformInputFiles(
                    runContext,
                    additionalVars,
                    inputs
                )
        );

        inputFiles
            .forEach(throwBiConsumer((fileName, input) ->
            {
                var file = runContext.workingDir().resolve(Path.of(runContext.render(fileName, additionalVars))).toFile();

                if (!file.getParentFile().exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.getParentFile().mkdirs();
                }

                if (input == null) {
                    if (!file.createNewFile()) {
                        throw new RuntimeException("Unable to create the file: " + file.getName());
                    }
                } else {
                    if (URIFetcher.supports(input)) {
                        var uri = URIFetcher.of(input);
                        try (
                            var is = new BufferedInputStream(uri.fetch(runContext), FileSerde.BUFFER_SIZE);
                            var out = new FileOutputStream(file)
                        ) {
                            IOUtils.copyLarge(is, out);
                        }
                    } else {
                        Files.write(file.toPath(), input.getBytes());
                    }
                }
            }));

        if (logger.isTraceEnabled()) {
            logger.trace("Provided {} input(s).", inputFiles.size());
        }

        return inputFiles;
    }

    public static Map<String, URI> outputFiles(RunContext runContext, List<String> outputs) throws Exception {
        return outputFiles(runContext, outputs, Set.of());
    }

    /** Collects configured patterns and literal runtime announcements, uploading each file once. */
    public static Map<String, URI> outputFiles(RunContext runContext, List<String> outputs, Collection<String> announced) throws Exception {
        Map<String, URI> uploaded = new HashMap<>();
        outputFiles(runContext, outputs, announced, null, uploaded);
        return uploaded;
    }

    /** Captures pre-command paths and physical identities, including files outside inputFiles declarations. */
    public static Map<Path, Object> stagedFiles(Path workingDir) throws IOException {
        Map<Path, Object> staged = new HashMap<>();
        try (var paths = Files.walk(workingDir)) {
            for (Path path : paths.toList()) {
                var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isDirectory()) {
                    staged.put(
                        path.toAbsolutePath().normalize(), attributes.isSymbolicLink() && Files.exists(path)
                            ? Files.readAttributes(path, BasicFileAttributes.class).fileKey()
                            : attributes.fileKey()
                    );
                }
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return staged;
    }

    /**
     * Collects all output candidates before uploading; uploaded retains completed files if collection fails.
     * Output-directory keys are added first, then working-directory keys take precedence.
     * Announcements require a filesystem supporting secure directory streams and cannot use symbolic links.
     */
    public static void outputFiles(RunContext runContext, List<String> outputs, Collection<String> announced,
        Path outputDir, Map<String, URI> uploaded) throws IOException, IllegalVariableEvaluationException {
        outputFiles(runContext, outputs, announced, outputDir, uploaded, Map.of());
    }

    /** Runtime announcements may not publish pre-command paths or aliases of their physical files. */
    public static void outputFiles(RunContext runContext, List<String> outputs, Collection<String> announced,
        Path outputDir, Map<String, URI> uploaded, Map<Path, Object> staged) throws IOException, IllegalVariableEvaluationException {
        Set<Path> dynamicFiles = new LinkedHashSet<>();
        Path workingDir = runContext.workingDir().path();
        for (String filename : announced) {
            if (filename == null) {
                throw new IOException("Announced output file paths must not be null.");
            }
            Path relative;
            try {
                relative = Path.of(filename);
            } catch (InvalidPathException e) {
                throw new IOException("Announced output file has an invalid path.", e);
            }
            if (
                relative.isAbsolute() || relative.toString().isBlank() ||
                    relative.getFileName().toString().equals(WorkingDir.EXECUTION_CONTEXT_FILE_NAME)
            ) {
                throw new IOException("Announced output file '%s' must be a relative file inside the task working directory.".formatted(filename));
            }
            for (Path part : relative) {
                if (part.toString().equals("..")) {
                    throw new IOException("Announced output file '%s' must not contain parent traversal.".formatted(filename));
                }
            }
            dynamicFiles.add(runContext.workingDir().resolve(relative.normalize()).normalize());
        }
        Map<String, Path> directoryFiles = outputDir == null ? Map.of() : ScriptService.outputFilePaths(outputDir);
        Set<Path> matchingFiles = new LinkedHashSet<>(dynamicFiles);
        List<String> renderedOutputs = outputs != null ? runContext.render(outputs) : null;
        runContext.workingDir().findAllFilesMatching(renderedOutputs).forEach(path -> matchingFiles.add(path.normalize()));
        if (dynamicFiles.isEmpty()) {
            uploadOutputFiles(runContext, directoryFiles, matchingFiles, dynamicFiles, null, uploaded, staged);
            return;
        }

        try (var directory = openSecureDirectory(workingDir)) {
            for (Path path : dynamicFiles) {
                BasicFileAttributes attributes;
                try {
                    attributes = announcedAttributes(directory, workingDir.normalize().relativize(path));
                } catch (IOException e) {
                    // Preserve existing partial collection when an announced file is missing or invalid.
                    continue;
                }
                rejectStagedFile(path, attributes, staged);
            }
            uploadOutputFiles(runContext, directoryFiles, matchingFiles, dynamicFiles, directory, uploaded, staged);
        }
    }

    /** Opens a task directory without following a replacement root symlink; the worker owns its parent. */
    public static SecureDirectoryStream<Path> openSecureDirectory(Path directory) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        try (var parent = Files.newDirectoryStream(root.getParent())) {
            if (!(parent instanceof SecureDirectoryStream<Path> secureParent)) {
                throw new IOException("Output files require a filesystem supporting SecureDirectoryStream.");
            }
            return secureParent.newDirectoryStream(root.getFileName(), LinkOption.NOFOLLOW_LINKS);
        }
    }

    private static void uploadOutputFiles(RunContext runContext, Map<String, Path> directoryFiles, Set<Path> matchingFiles,
        Set<Path> dynamicFiles, SecureDirectoryStream<Path> directory, Map<String, URI> uploaded, Map<Path, Object> staged) throws IOException {
        List<OutputFile> candidates = new ArrayList<>();
        directoryFiles.forEach((key, path) -> candidates.add(new OutputFile(path.normalize(), key, key)));
        matchingFiles.forEach(path -> candidates.add(new OutputFile(path, runContext.workingDir().path().normalize().relativize(path).toString(), resolveUniqueNameForFile(path))));
        Map<Path, Object> identities = new HashMap<>();
        Map<Object, Path> sources = new HashMap<>();
        Map<Path, IOException> failures = new HashMap<>();
        for (var candidate : candidates) {
            Path path = candidate.path();
            if (identities.containsKey(path)) {
                continue;
            }
            Object identity = path;
            try {
                var attributes = dynamicFiles.contains(path)
                    ? announcedAttributes(directory, runContext.workingDir().path().normalize().relativize(path))
                    : Files.readAttributes(path, BasicFileAttributes.class);
                if (attributes.fileKey() != null) {
                    identity = List.of(path.getFileSystem(), attributes.fileKey());
                } else {
                    identity = path.toRealPath();
                    for (var source : sources.entrySet()) {
                        if (Files.isSameFile(path, source.getValue())) {
                            identity = source.getKey();
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                failures.put(path, e);
            }
            identities.put(path, identity);
            if (dynamicFiles.contains(path) || !sources.containsKey(identity)) {
                sources.put(identity, path);
            }
        }
        Map<Object, URI> persisted = new HashMap<>();
        for (var candidate : candidates) {
            Path path = candidate.path();
            if (failures.containsKey(path)) {
                throw failures.get(path);
            }
            Object identity = identities.get(path);
            URI uri = persisted.get(identity);
            if (uri == null) {
                Path source = sources.get(identity);
                uri = uploadOutputFile(runContext, source, candidate.storageName(), dynamicFiles, directory, staged);
                persisted.put(identity, uri);
            }
            uploaded.put(candidate.key(), uri);
        }
        // Delay alias cleanup until every key has its URI; all identities were captured before deletion.
        for (Path path : identities.keySet()) {
            if (persisted.containsKey(identities.get(path))) {
                try {
                    if (directory == null) {
                        Files.deleteIfExists(path);
                    } else {
                        deleteAnnouncedFile(directory, runContext.workingDir().path().normalize().relativize(path));
                    }
                } catch (IOException e) {
                    runContext.logger().warn("Failed to delete temporary output file '{}'", path, e);
                }
            }
        }
        runContext.logger().trace("Captured {} output file(s).", persisted.size());
    }

    private record OutputFile(Path path, String key, String storageName) {
    }

    private static BasicFileAttributes announcedAttributes(SecureDirectoryStream<Path> directory, Path relative) throws IOException {
        if (relative.getNameCount() > 1) {
            try (var child = directory.newDirectoryStream(relative.getName(0), LinkOption.NOFOLLOW_LINKS)) {
                return announcedAttributes(child, relative.subpath(1, relative.getNameCount()));
            }
        }
        var attributes = directory.getFileAttributeView(relative, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
        if (!attributes.isRegularFile()) {
            throw new IOException("Announced output file '%s' must be a regular file and must not use symbolic links.".formatted(relative));
        }
        return attributes;
    }

    private static void deleteAnnouncedFile(SecureDirectoryStream<Path> directory, Path relative) throws IOException {
        try {
            if (relative.getNameCount() > 1) {
                try (var child = directory.newDirectoryStream(relative.getName(0), LinkOption.NOFOLLOW_LINKS)) {
                    deleteAnnouncedFile(child, relative.subpath(1, relative.getNameCount()));
                }
            } else {
                directory.deleteFile(relative);
            }
        } catch (NoSuchFileException ignored) {
            // Another spelling of this file was already removed by its upload.
        }
    }

    private static URI uploadOutputFile(RunContext runContext, Path path, String storageName, Set<Path> dynamicFiles,
        SecureDirectoryStream<Path> directory, Map<Path, Object> staged) throws IOException {
        if (!dynamicFiles.contains(path)) {
            return runContext.storage().putFile(path.toFile(), storageName);
        }
        return uploadAnnouncedFile(runContext, directory, runContext.workingDir().path().normalize().relativize(path), storageName, staged, path);
    }

    private static URI uploadAnnouncedFile(RunContext runContext, SecureDirectoryStream<Path> directory, Path relative, String storageName, Map<Path, Object> staged, Path path)
        throws IOException {
        if (relative.getNameCount() > 1) {
            try (var child = directory.newDirectoryStream(relative.getName(0), LinkOption.NOFOLLOW_LINKS)) {
                return uploadAnnouncedFile(runContext, child, relative.subpath(1, relative.getNameCount()), storageName, staged, path);
            }
        }
        var attributes = directory.getFileAttributeView(relative, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
        if (!attributes.isRegularFile()) {
            throw new IOException("Announced output file '%s' must be a regular file and must not use symbolic links.".formatted(relative));
        }
        rejectStagedFile(path, attributes, staged);
        URI uri;
        try (
            var channel = directory.newByteChannel(relative, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
            var input = Channels.newInputStream(channel)
        ) {
            uri = runContext.storage().putFile(input, storageName);
        }
        try {
            directory.deleteFile(relative);
        } catch (IOException e) {
            runContext.logger().warn("Failed to delete temporary output file '{}'", relative, e);
        }
        return uri;
    }

    private static void rejectStagedFile(Path path, BasicFileAttributes attributes, Map<Path, Object> staged) throws IOException {
        if (staged.containsKey(path.toAbsolutePath().normalize()) || attributes.fileKey() != null && staged.containsValue(attributes.fileKey())) {
            throw new IOException("Announced output file '%s' was staged before command execution.".formatted(path.getFileName()));
        }
        if (!staged.isEmpty() && (attributes.fileKey() == null || staged.containsValue(null))) {
            throw new IOException("Announced output files require filesystem identities to exclude staged files.");
        }
    }

    private static String resolveUniqueNameForFile(final Path path) {
        String filename = path.getFileName().toString().replace(' ', '+');
        return IdUtils.from(path.toString()) + "-" + filename;
    }
}
