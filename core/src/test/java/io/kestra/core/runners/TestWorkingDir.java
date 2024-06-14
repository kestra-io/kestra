package io.kestra.core.runners;

import io.kestra.core.utils.IdUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * A delegating {@link WorkingDir} implementation that can be used for testing purpose.
 */
public final class TestWorkingDir implements WorkingDir {

    private final String id;
    private final WorkingDir delegate;
    private final List<Path> allCreatedTempFiles = new ArrayList<>();
    private final List<Path> allCreatedFiles = new ArrayList<>();
    private boolean isCleaned = false;

    public static TestWorkingDir create() {
        String id = IdUtils.create();
        return new TestWorkingDir(id, new LocalWorkingDir(Path.of("/tmp"), id));
    }

    public static TestWorkingDir create(final String tmpdirBasePath) {
        String id = IdUtils.create();
        return new TestWorkingDir(id, new LocalWorkingDir(Path.of(tmpdirBasePath), id));
    }

    public TestWorkingDir(final String id, final WorkingDir delegate) {
        this.id = id;
        this.delegate = delegate;
    }

    public String id() {
        return id;
    }

    @Override
    public Path path() {
        return delegate.path();
    }

    @Override
    public Path path(boolean create) {
        return delegate.path(create);
    }

    @Override
    public Path resolve(Path path) {
        return delegate.resolve(path);
    }

    @Override
    public Path createTempFile() throws IOException {
        return captureCreateTempFileAndGet(delegate.createTempFile());
    }

    @Override
    public Path createTempFile(String extension) throws IOException {
        return captureCreateTempFileAndGet(delegate.createTempFile(extension));
    }

    @Override
    public Path createTempFile(byte[] content) throws IOException {
        return captureCreateTempFileAndGet(delegate.createTempFile(content));
    }

    @Override
    public Path createTempFile(byte[] content, String extension) throws IOException {
        return captureCreateFileAndGet(delegate.createTempFile(content, extension));
    }

    @Override
    public Path createFile(String filename) throws IOException {
        return captureCreateFileAndGet(delegate.createFile(filename));
    }

    @Override
    public Path createFile(String filename, byte[] content) throws IOException {
        return captureCreateFileAndGet(delegate.createFile(filename, content));
    }

    @Override
    public Path createFile(String filename, InputStream content) throws IOException {
        return captureCreateFileAndGet(delegate.createFile(filename, content));
    }

    @Override
    public Path putFile(Path path, InputStream content) throws IOException {
        return captureCreateFileAndGet(delegate.putFile(path, content));
    }

    @Override
    public List<Path> findAllFilesMatching(List<String> patterns) throws IOException {
        return delegate.findAllFilesMatching(patterns);
    }

    @Override
    public void cleanup() throws IOException {
        delegate.cleanup();
        this.isCleaned = true;
    }

    /**
     * Checks whether this working-dir has been cleaned.
     *
     * @return {@code true} if cleaned, otherwise {@code false}.
     */
    public  boolean isCleaned() {
        return isCleaned;
    }

    /**
     * Gets the list of all standard files and temporary files created in this working directory.
     *
     * @return list of {@link Path paths}
     */
    public List<Path> getAllCreatedFilesAndTempFiles() {
        return Stream.concat(allCreatedTempFiles.stream(), allCreatedFiles.stream()).toList();
    }
    /**
     * Gets the list of all standard files created in this working directory.
     *
     * @return list of {@link Path paths}
     */
    public List<Path> getAllCreatedFiles() {
        return Collections.unmodifiableList(allCreatedFiles);
    }
    /**
     * Gets the list of all temporary files created in this working directory.
     *
     * @return list of {@link Path paths}
     */
    public List<Path> getAllCreatedTempFiles() {
        return Collections.unmodifiableList(allCreatedTempFiles);
    }

    private Path captureCreateTempFileAndGet(final Path path) {
        this.allCreatedTempFiles.add(path);
        return path;
    }

    private Path captureCreateFileAndGet(final Path path) {
        this.allCreatedFiles.add(path);
        return path;
    }
}
