package io.kestra.core.storages;

import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.utils.PathMatcherPredicate;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Service interface for accessing the files attached to a namespace (a.k.a., Namespace Files).
 */
public interface Namespace {
    String NAMESPACE_FILE_SCHEME = "nsfile";

    ArrayListTotal<NamespaceFile> find(Pageable pageable, List<QueryFilter> filters, boolean allowDeleted, FetchVersion fetchVersion);

    /**
     * Gets the current namespace.
     *
     * @return the current namespace.
     */
    String namespace();

    /**
     * Gets the current tenantId.
     *
     * @return the current tenantId.
     */
    String tenantId();

    /**
     * Gets the URIs of all namespace files for the contextual namespace.
     *
     * @return The list of {@link URI}.
     */
    List<NamespaceFile> all() throws IOException;

    default List<NamespaceFile> all(String containing) throws IOException {
        return this.all(containing, false);
    }

    /**
     * Gets the URIs of all namespace files for the current namespace that contains the optional <code>containing</code> parameter.
     *
     * @return The list of {@link URI}.
     */
    List<NamespaceFile> all(String containing, boolean includeDirectories) throws IOException;

    /**
     * Gets the URIs of all namespace files for the current namespace under the <code>parentPath</code>.
     *
     * @return The list of {@link URI}.
     */
    List<NamespaceFileMetadata> children(String parentPath, boolean recursive) throws IOException;

    List<Pair<NamespaceFile, NamespaceFile>> move(Path source, Path target) throws Exception;

    /**
     * Gets a {@link NamespaceFile} for the given path and the current namespace.
     *
     * @param path the file path.
     * @return a new {@link NamespaceFile}
     */
    NamespaceFile get(Path path) throws IOException;

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
     * Retrieves the content of the namespace file at the given path for the latest version.
     */
    default InputStream getFileContent(Path path) throws IOException {
        return getFileContent(path, null);
    }

    /**
     * Retrieves the content of the namespace file at the given path.
     *
     * @param path the file path.
     * @param version optionally a file version, otherwise will retrieve the latest.
     * @return the {@link InputStream}.
     * @throws IllegalArgumentException if the given {@link Path} is {@code null} or invalid.
     * @throws IOException              if an error happens while accessing the file.
     */
    InputStream getFileContent(Path path, @Nullable Integer version) throws IOException;

    /**
     * Retrieves the metadata of the namespace file at the given path.
     *
     * @param path the file path.
     * @return the {@link FileAttributes}.
     */
    FileAttributes getFileMetadata(Path path) throws IOException;

    boolean exists(Path path) throws IOException;

    default List<NamespaceFile> putFile(Path path, InputStream content) throws IOException, URISyntaxException {
        return putFile(path, content, Conflicts.OVERWRITE);
    }

    List<NamespaceFile> putFile(Path path, InputStream content, Conflicts onAlreadyExist) throws IOException, URISyntaxException;

    default List<NamespaceFile> putFile(NamespaceFile file, InputStream content) throws IOException, URISyntaxException {
        return putFile(file, content, Conflicts.OVERWRITE);
    }

    default List<NamespaceFile> putFile(NamespaceFile file, InputStream content, Conflicts onAlreadyExist) throws IOException, URISyntaxException {
        return putFile(Path.of(file.path()), content, onAlreadyExist);
    }

    /**
     * Creates a new directory for the current namespace.
     *
     * @param path The {@link Path} of the directory.
     * @return The created namespace file.
     * @throws IOException if an error happens while accessing the file.
     */
    NamespaceFile createDirectory(Path path) throws IOException;

    /**
     * Deletes any namespaces file at the given path.
     *
     * @param file the {@link NamespaceFile} to be deleted.
     * @throws IOException if an error happens while performing the delete operation.
     */
    default List<NamespaceFile> delete(NamespaceFile file) throws IOException {
        return delete(Path.of(file.path()));
    }

    /**
     * Soft-deletes any namespaces files at the given path.
     *
     * @param path the path to be deleted.
     * @return the list of namespace files that got deleted. There can be multiple files if a directory is deleted as its whole content will be.
     * @throws IOException if an error happens while performing the delete operation.
     */
    List<NamespaceFile> delete(Path path) throws IOException;

    /**
     * Hard-deletes any namespaces files.
     *
     * @param namespaceFile the namespace file to be purged.
     * @return {@code true} if the file was purged by this method; {@code false} if the file could not be deleted because it did not exist
     * @throws IOException if an error happens while performing the delete operation.
     */
    boolean purge(NamespaceFile namespaceFile) throws IOException;

    /**
     * Hard-deletes all provided namespaces files.
     *
     * @param namespaceFiles the namespace files to be purged.
     * @return the amount of files that were purged.
     * @throws IOException if an error happens while performing the delete operation.
     */
    Integer purge(List<NamespaceFile> namespaceFiles) throws IOException;

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
