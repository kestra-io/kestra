package io.kestra.core.runners;

import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.PathMatcherPredicate;
import org.apache.commons.io.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

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
        return createFile(filename, (InputStream) null);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Path createFile(String filename, byte[] content) throws IOException {
        return createFile(filename, content == null ? null : new ByteArrayInputStream(content));
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Path createFile(String filename, InputStream content) throws IOException {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Cannot create a working directory file with a null or empty name");
        }
        Path newFilePath = this.resolve(Path.of(filename));
        Files.createDirectories(newFilePath.getParent());

        Files.createFile(newFilePath);

        if (content != null) {
            try (content) {
                Files.copy(content, newFilePath, REPLACE_EXISTING);
            }
        }

        return newFilePath;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Path putFile(Path path, InputStream inputStream) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Cannot create a working directory file with a null path");
        }
        if (inputStream == null) {
            throw new IllegalArgumentException("Cannot create a working directory file with an empty inputStream");
        }
        Path newFilePath = this.resolve(path);
        Files.createDirectories(newFilePath.getParent());

        if (Files.notExists(newFilePath)) {
            Files.createFile(newFilePath);
        }

        try (inputStream) {
            Files.copy(inputStream, newFilePath, REPLACE_EXISTING);
        }

        return newFilePath;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<Path> findAllFilesMatching(final List<String> patterns) throws IOException {
        if (patterns == null || patterns.isEmpty()) {
            return Collections.emptyList();
        }
        MatcherFileVisitor visitor = new MatcherFileVisitor(PathMatcherPredicate.matches(path(), patterns));
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

        private final Predicate<Path> predicate;
        private final List<Path> matchedFiles = new ArrayList<>();

        public MatcherFileVisitor(final Predicate<Path> predicate) {
            this.predicate = predicate;
        }

        /**
         * {@inheritDoc}
         **/
        @Override
        public FileVisitResult visitFile(final Path path, final BasicFileAttributes basicFileAttributes) {
            if (!basicFileAttributes.isRegularFile()) {
                // make sure we never follow symlink
                return FileVisitResult.CONTINUE;
            }

            if (predicate.test(path)) {
                matchedFiles.add(path);
            }

            return FileVisitResult.CONTINUE;
        }

        public List<Path> getMatchedFiles() {
            return matchedFiles;
        }
    }
}
