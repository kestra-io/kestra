package io.kestra.core.runners;

import io.kestra.core.utils.IdUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation of the {@link WorkingDir}.
 */
public class LocalWorkingDir implements WorkingDir {

    private final Path workingDirPath;

    /**
     * Creates a new {@link LocalWorkingDir} instance.
     *
     * @param tmpdirBasePath The base temporary directory for this working-dir.
     */
    public LocalWorkingDir(final Path tmpdirBasePath) {
        this(tmpdirBasePath, IdUtils.create());
    }

    /**
     * Creates a new {@link LocalWorkingDir} instance.
     *
     * @param tmpdirBasePath The base temporary directory for this working-dir.
     * @param workingDirId   The working directory id.
     */
    public LocalWorkingDir(final Path tmpdirBasePath, final String workingDirId) {
        this.workingDirPath = tmpdirBasePath.resolve(workingDirId);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Path path() {
        return path(true);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public synchronized Path path(boolean create) {
        if (create && !this.workingDirPath.toFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            this.workingDirPath.toFile().mkdirs();
        }
        return this.workingDirPath;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Path resolve(Path path) {
        if (path == null) {
            return path();
        }

        if (path.toString().contains(".." + File.separator)) {
            throw new IllegalArgumentException("The path to resolve must be a relative path inside the current working directory.");
        }

        Path baseDir = path();
        Path resolved = baseDir.resolve(path).toAbsolutePath();

        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("The path to resolve must be a relative path inside the current working directory.");
        }

        return resolved;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Path createTempFile() throws IOException {
        return createTempFile(null, null);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Path createTempFile(final String extension) throws IOException {
        return createTempFile(null, extension);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Path createTempFile(final byte[] content) throws IOException {
        return createTempFile(content, null);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Path createTempFile(final byte[] content, final String extension) throws IOException {
        String suffix = extension != null && !extension.startsWith(".") ? "." + extension : extension;
        Path tempFile = Files.createTempFile(this.path(), null, suffix);
        if (content != null) {
            Files.write(tempFile, content);
        }
        return tempFile;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Path createFile(String filename) throws IOException {
        return createFile(filename, null);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Path createFile(String filename, byte[] content) throws IOException {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Cannot create a working directory file with a null or empty name");
        }
        Path newFilePath = this.resolve(Path.of(filename));
        Files.createDirectories(newFilePath.getParent());
        Path file = Files.createFile(newFilePath);

        if (content != null) {
            Files.write(file, content);
        }

        return file;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<Path> findAllFilesMatching(final List<String> patterns) throws IOException {
        if (patterns == null || patterns.isEmpty()) {
            return Collections.emptyList();
        }
        MatcherFileVisitor visitor = new MatcherFileVisitor(path(), patterns);
        Files.walkFileTree(path(), visitor);
        return visitor.getMatchedFiles();
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public void cleanup() throws IOException {
        if (workingDirPath != null && Files.exists(workingDirPath)) {
            FileUtils.deleteDirectory(workingDirPath.toFile());
        }
    }

    private static class MatcherFileVisitor extends SimpleFileVisitor<Path> {

        private static final String SYNTAX_GLOB = "glob:";
        private static final String SYNTAX_REGEX = "regex:";

        private final List<PathMatcher> matchers;
        private final List<Path> matchedFiles = new ArrayList<>();

        public MatcherFileVisitor(final Path basePath, final List<String> patterns) {
            FileSystem fs = FileSystems.getDefault();
            this.matchers = patterns.stream()
                .map(pattern -> {
                    var syntaxAndPattern = isPrefixWithSyntax(pattern) ? pattern : SYNTAX_GLOB + basePath + addLeadingSlash(pattern);
                    return fs.getPathMatcher(syntaxAndPattern);
                })
                .toList();
        }

        /** {@inheritDoc} **/
        @Override
        public FileVisitResult visitFile(final Path path, final BasicFileAttributes basicFileAttributes) {
            if (!basicFileAttributes.isRegularFile()) {
                // make sure we never follow symlink
                return FileVisitResult.CONTINUE;
            }

            if (matchers.stream().anyMatch(pathMatcher -> pathMatcher.matches(path))) {
                matchedFiles.add(path);
            }

            return FileVisitResult.CONTINUE;
        }

        public List<Path> getMatchedFiles() {
            return matchedFiles;
        }

        private static String addLeadingSlash(final String path) {
            return path.startsWith("/") ? path : "/" + path;
        }

        private static boolean isPrefixWithSyntax(final String pattern) {
            return pattern.startsWith(SYNTAX_REGEX) | pattern.startsWith(SYNTAX_GLOB);
        }
    }
}
