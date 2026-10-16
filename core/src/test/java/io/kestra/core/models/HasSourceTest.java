package io.kestra.core.models;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.FormFieldMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HasSourceTest {

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

    @Test
    @ResourceLock(Resources.LOCALE)
    void shouldImportZipUnderTurkishLocale() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("flow.yaml"));
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
            assertThat(imported).contains("flow.yaml");
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    void shouldImportYamlWithUppercaseExtensionInsideZip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("flow.YAML"));
            zos.write("id: test1\nnamespace: company.team\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("sub/flow.YML"));
            zos.write("id: test2\nnamespace: company.team\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        CompletedFileUpload fileUpload = CompletedFileUpload.ofMemory(
            new FormFieldMetadata("file", "flows.zip", null),
            ReadBufferFactory.getJdkFactory().adapt(baos.toByteArray())
        );

        List<String> imported = new ArrayList<>();
        HasSource.readSourceFile(null, fileUpload, (source, name) -> imported.add(name));
        assertThat(imported).containsExactlyInAnyOrder("flow.YAML", "sub/flow.YML");
    }

    @Test
    void shouldImportDirectUppercaseYaml() throws Exception {
        byte[] yamlBytes = "id: direct\nnamespace: company.team\n".getBytes(StandardCharsets.UTF_8);
        CompletedFileUpload fileUpload = CompletedFileUpload.ofMemory(
            new FormFieldMetadata("file", "flow.YAML", null),
            ReadBufferFactory.getJdkFactory().adapt(yamlBytes)
        );

        List<String> imported = new ArrayList<>();
        HasSource.readSourceFile(null, fileUpload, (source, name) -> imported.add(source));
        assertThat(imported).hasSize(1);
        assertThat(imported.getFirst()).contains("id: direct");
    }
}
