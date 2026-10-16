package io.kestra.core.models;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.FormFieldMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HasSourceTest {

    @Test
    void shouldImportUppercaseZipOnTurkishLocale() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("flow.YAML"));
            zos.write("id: test\nnamespace: company.team\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        CompletedFileUpload fileUpload = CompletedFileUpload.ofMemory(
            new FormFieldMetadata("file", "flows.ZIP", null),
            ReadBufferFactory.getJdkFactory().adapt(baos.toByteArray())
        );

        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            List<String> imported = new ArrayList<>();
            HasSource.readSourceFile(null, fileUpload, (source, name) -> imported.add(name));
            assertThat(imported).containsExactly("flow.YAML");
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    void shouldThrowIllegalArgumentWhenImportingFileWithoutExtension() throws Exception {
        // Given a file whose name has no extension
        CompletedFileUpload fileUpload = CompletedFileUpload.ofMemory(
            new FormFieldMetadata("file", "no-extension", null),
            ReadBufferFactory.getJdkFactory().adapt(new byte[0])
        );

        // When - Then the unsupported type is reported as an IllegalArgumentException
        // instead of a StringIndexOutOfBoundsException raised by substring(-1)
        assertThatThrownBy(() -> HasSource.readSourceFile(null, fileUpload, (source, name) ->
        {
        }))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot import file of type");
    }
}
