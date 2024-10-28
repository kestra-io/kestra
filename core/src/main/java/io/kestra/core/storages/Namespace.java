package io.kestra.core.storages;

import io.kestra.core.utils.PathMatcherPredicate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/**
 * Service interface for accessing the files attached to a namespace (a.k.a., Namespace Files).
 */
public interface Namespace {

    /**
     * Gets the current namespace.
     *
     * @return the current namespace.
     */
    String namespace();

    /**
     * Gets the URIs of all namespace files for the contextual namespace.
     *
     * @return The list of {@link URI}.
     */
    List<NamespaceFile> all() throws IOException;

    /**
     * Gets the URIs of all namespace files for the contextual namespace.
     *
     * @return The list of {@link URI}.
     */
    List<NamespaceFile> all(boolean includeDirectories) throws IOException;

    /**
     * Gets the URIs of all namespace files for the current namespace.
     *
     * @return The list of {@link URI}.
     */
    List<NamespaceFile> all(String prefix, boolean includeDirectories) throws IOException;

    /**
     * Gets a {@link NamespaceFile} for the given path and the current namespace.
     *
     * @param path the file path.
     * @return a new {@link NamespaceFile}
     */
    NamespaceFile get(Path path);

    /**
     * Retrieves the URIs of all namespace files for the current namespace matching the given predicate.
     *
     * @param predicate The predicate for matching files.
     * @return The list of {@link URI} for matched namespace files.
     */
    List<NamespaceFile> findAllFilesMatching(Predicate<Path> predicate) throws IOException;

    /**
     * Retrieves the URIs of all namespace files for the current namespace matching the given predicates.
     *
     * @param includes A list of glob expressions specifying the files to include.
     * @param excludes A list of glob expressions specifying the files to exclude.
     * @return A list of {@link URI} objects representing the matched namespace files.
     */
    default List<NamespaceFile> findAllFilesMatching(List<String> includes, List<String> excludes) throws IOException {
        Predicate<Path> predicate = PathMatcherPredicate.builder()
            .includes(includes)
            .excludes(excludes)
            .build();
        return findAllFilesMatching(predicate);
    }

    /**
     * Retrieves the content of the namespace file at the given path.
     *
     * @param path the file path.
     * @return the {@link InputStream}.
     * @throws IllegalArgumentException if the given {@link Path} is {@code null} or invalid.
     * @throws IOException              if an error happens while accessing the file.
     */
    InputStream getFileContent(Path path) throws IOException;

    default NamespaceFile putFile(Path path, InputStream content) throws IOException, URISyntaxException {
        return putFile(path, content, Conflicts.OVERWRITE);
    }

    NamespaceFile putFile(Path path, InputStream content, Conflicts onAlreadyExist) throws IOException, URISyntaxException;

    default NamespaceFile putFile(NamespaceFile file, InputStream content) throws IOException, URISyntaxException {
        return putFile(file, content, Conflicts.OVERWRITE);
    }

    default NamespaceFile putFile(NamespaceFile file, InputStream content, Conflicts onAlreadyExist) throws IOException, URISyntaxException {
        return putFile(Path.of(file.path()), content, onAlreadyExist);
    }

    /**
     * Creates a new directory for the current namespace.
     *
     * @param path The {@link Path} of the directory.
     * @return The URI of the directory in the Kestra's internal storage.
     * @throws IOException if an error happens while accessing the file.
     */
    URI createDirectory(Path path) throws IOException;

    /**
     * Deletes any namespaces files at the given path.
     *
     * @param file the {@link NamespaceFile} to be deleted.
     * @throws IOException if an error happens while performing the delete operation.
     */
    default boolean delete(NamespaceFile file) throws IOException {
        return delete(Path.of(file.path()));
    }

    /**
     * Deletes namespaces directories at the given path.
     *
     * @param file the {@link NamespaceFile} to be deleted.
     * @throws IOException if an error happens while performing the delete operation.
     */
    default boolean deleteDirectory(NamespaceFile file) throws IOException {
        return delete(Path.of(file.path()));
    }

    /**
     * Deletes any namespaces files at the given path.
     *
     * @param path the path to be deleted.
     * @return {@code true} if the file was deleted by this method; {@code false} if the file could not be deleted because it did not exist
     * @throws IOException if an error happens while performing the delete operation.
     */
    boolean delete(Path path) throws IOException;

    /**
     * Checks if a directory is empty.
     *
     * @param path the directory path to check
     * @return true if the directory is empty or doesn't exist, false otherwise
     * @throws IOException if an error occurs while checking the directory
     */
    default boolean isDirectoryEmpty(String path) throws IOException {
        List<NamespaceFile> files = findAllFilesMatching(
            List.of(path + "/**"),
            List.of()
        );
        return files.isEmpty();
    }

    enum Conflicts {
        OVERWRITE,
        ERROR,
        SKIP
    }

}
