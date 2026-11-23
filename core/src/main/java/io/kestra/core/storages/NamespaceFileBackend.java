package io.kestra.core.storages;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Holds the content of the namespace files, one object per revision, while their metadata goes to the
 * {@link io.kestra.core.namespace.NamespaceFileMetadataStateStore}. An implementation decides where the content
 * lives; {@link InternalNamespace} keeps the metadata in step with it. Content is passed as streams, so an
 * implementation relaying it is not bound to hold a whole file in memory.
 * <p>
 * A {@link NamespaceFile} addresses one object: the revision it carries is the one read or written, and a file whose
 * path ends with a slash is a directory.
 */
public interface NamespaceFileBackend {

    /**
     * Opens the content of a revision of a namespace file.
     *
     * @param tenant the tenant owning the namespace.
     * @param file the file and revision to read.
     * @return the content, to be closed by the caller.
     * @throws FileNotFoundException if the revision has no content.
     * @throws IOException if the content cannot be reached.
     */
    InputStream get(String tenant, NamespaceFile file) throws IOException;

    /**
     * Checks whether a revision of a namespace file has content, without reading it.
     *
     * @param tenant the tenant owning the namespace.
     * @param file the file and revision to look for.
     * @return {@code true} if the revision has content.
     * @throws IOException if the content cannot be reached.
     */
    boolean exists(String tenant, NamespaceFile file) throws IOException;

    /**
     * Stores the content of a revision of a namespace file.
     *
     * @param tenant the tenant owning the namespace.
     * @param file the file and revision to write.
     * @param content the content, read to its end and closed.
     * @return the size of the stored content, in bytes.
     * @throws IOException if the content cannot be stored.
     */
    long put(String tenant, NamespaceFile file, InputStream content) throws IOException;

    /**
     * Creates a directory of the namespace files.
     *
     * @param tenant the tenant owning the namespace.
     * @param directory the directory, whose path ends with a slash.
     * @throws IOException if the directory cannot be created.
     */
    void createDirectory(String tenant, NamespaceFile directory) throws IOException;

    /**
     * Deletes the content of a revision of a namespace file. Its metadata is left to the caller.
     *
     * @param tenant the tenant owning the namespace.
     * @param file the file and revision to delete.
     * @return {@code true} if content was deleted, {@code false} if there was none.
     * @throws IOException if the content cannot be reached.
     */
    boolean delete(String tenant, NamespaceFile file) throws IOException;
}
