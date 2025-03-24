package io.kestra.core.models;

import io.micronaut.http.multipart.CompletedFileUpload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Interface that can be implemented by Kestra's resource attached to an original source code.
 */
public interface HasSource {

    /**
     * Gets the source of this Kestra's resource.
     * <p>
     * This method should return a valid and parseable in YAML object.
     *
     * @return  the string source.
     */
    String source();

    /**
     * Static helper method for constructing a ZIP file containing the given sources.
     *
     * @param sources      the sources to zip.
     * @param zipEntryName the function used for constructing the ZIP entry name.
     * @param <T>          type of the source.
     * @return a byte array representation of the ZIP file.
     * @throws IOException if an error happen while zipping sources.
     */
    static <T extends HasSource> byte[] asZipFile(final List<? extends T> sources,
                                                  final Function<T, String> zipEntryName) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream archive = new ZipOutputStream(bos)) {

            for (var source : sources) {
                var zipEntry = new ZipEntry(zipEntryName.apply(source));
                archive.putNextEntry(zipEntry);
                archive.write(source.source().getBytes());
                archive.closeEntry();
            }
            archive.finish();
            return bos.toByteArray();
        }
    }

    /**
     * Static helper method for reading an uploaded source or archive file.
     *
     * @param fileUpload    the upload file.
     * @param reader        the source reader.
     * @throws IOException  if the file cannot be read.
     */
    static void readSourceFile(final CompletedFileUpload fileUpload, final BiConsumer<String, String> reader) throws IOException {
        String fileName = fileUpload.getFilename().toLowerCase();
        try (InputStream inputStream = fileUpload.getInputStream()) {

            if (isYAML(fileName)) {
                byte[] bytes = inputStream.readAllBytes();
                List<String> sources = List.of(new String(bytes).split("---"));
                for (int i = 0; i < sources.size(); i++) {
                    String source = sources.get(i);
                    reader.accept(source, String.valueOf(i));
                }
            } else if (fileName.endsWith(".zip")) {

                try (ZipInputStream archive = new ZipInputStream(inputStream)) {
                    ZipEntry entry;
                    while ((entry = archive.getNextEntry()) != null) {
                        if (entry.isDirectory() || !isYAML(entry.getName())) {
                            continue;
                        }
                        reader.accept(new String(archive.readAllBytes()), entry.getName());
                    }
                }
            } else {
                throw new IllegalArgumentException("Cannot import file of type " + fileName.substring(fileName.lastIndexOf('.')));
            }
        }
    }

    private static boolean isYAML(final String fileName) {
        return fileName.endsWith(".yaml") || fileName.endsWith(".yml");
    }
}
