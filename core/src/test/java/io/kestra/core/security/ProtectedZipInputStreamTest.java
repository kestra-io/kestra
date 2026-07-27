package io.kestra.core.security;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import io.kestra.core.security.ProtectedZipInputStream.ZipBombDetectedException;
import io.kestra.core.security.SecurityConfiguration.ZipBombProtectionConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtectedZipInputStreamTest {

    private static byte[] zipOf(String... entryContents) throws IOException {
        try (
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ZipOutputStream archive = new ZipOutputStream(bos)
        ) {
            for (int i = 0; i < entryContents.length; i++) {
                archive.putNextEntry(new ZipEntry("entry-" + i + ".yaml"));
                archive.write(entryContents[i].getBytes(StandardCharsets.UTF_8));
                archive.closeEntry();
            }
            archive.finish();
            return bos.toByteArray();
        }
    }

    @Test
    void shouldReadAllEntriesWhenWithinLimits() throws IOException {
        // Given a ZIP with 2 small entries and a config allowing up to 3 entries of 100 bytes
        byte[] zip = zipOf("hello", "world");
        ZipBombProtectionConfiguration config = new ZipBombProtectionConfiguration(true, 3, 100);

        // When reading all entries through the protected stream
        int count = 0;
        try (ZipInputStream archive = ProtectedZipInputStream.of(new ByteArrayInputStream(zip), config)) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                assertThat(archive.readAllBytes()).isNotEmpty();
                count++;
            }
        }

        // Then every entry was read successfully
        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldSucceedWhenExactlyAtEntryCountAndSizeLimit() throws IOException {
        // Given a ZIP with exactly as many entries, and exactly as large a payload, as the config allows
        byte[] zip = zipOf("12345", "67890");
        ZipBombProtectionConfiguration config = new ZipBombProtectionConfiguration(true, 2, 5);

        // When reading every entry through the protected stream
        int count = 0;
        try (ZipInputStream archive = ProtectedZipInputStream.of(new ByteArrayInputStream(zip), config)) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                assertThat(archive.readAllBytes()).hasSize(5);
                count++;
            }
        }

        // Then no limit is exceeded: exactly maxEntries entries of exactly maxEntrySize bytes each succeed
        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldThrowZipBombDetectedExceptionWhenTooManyEntries() throws IOException {
        // Given a ZIP with 3 entries but a config allowing only 2
        byte[] zip = zipOf("a", "b", "c");
        ZipBombProtectionConfiguration config = new ZipBombProtectionConfiguration(true, 2, 100);

        // When reading past the allowed number of entries
        // Then a ZipBombDetectedException is thrown, naming the violated config key
        assertThatThrownBy(() -> {
            try (ZipInputStream archive = ProtectedZipInputStream.of(new ByteArrayInputStream(zip), config)) {
                while (archive.getNextEntry() != null) {
                    archive.readAllBytes();
                }
            }
        })
            .isInstanceOf(ZipBombDetectedException.class)
            .hasMessageContaining("more than 2 entries")
            .hasMessageContaining("kestra.security.zip-bomb-protection.max-number-of-entries");
    }

    @Test
    void shouldThrowZipBombDetectedExceptionWhenEntryTooLarge() throws IOException {
        // Given a single entry larger than the configured max entry size
        byte[] zip = zipOf("this content is way larger than the tiny configured limit");
        ZipBombProtectionConfiguration config = new ZipBombProtectionConfiguration(true, 10, 10);

        // When fully reading that entry
        // Then a ZipBombDetectedException is thrown before the whole entry is buffered
        assertThatThrownBy(() -> {
            try (ZipInputStream archive = ProtectedZipInputStream.of(new ByteArrayInputStream(zip), config)) {
                archive.getNextEntry();
                archive.readAllBytes();
            }
        })
            .isInstanceOf(ZipBombDetectedException.class)
            .hasMessageContaining("exceeds 10 bytes")
            .hasMessageContaining("kestra.security.zip-bomb-protection.max-entry-size");
    }

    @Test
    void shouldReturnPlainZipInputStreamWhenConfigDisabled() throws IOException {
        // Given a ZIP exceeding a disabled config's limits
        byte[] zip = zipOf("a", "b", "c");
        ZipBombProtectionConfiguration disabled = new ZipBombProtectionConfiguration(false, 1, 1);

        // When reading through the returned stream
        int count = 0;
        try (ZipInputStream archive = ProtectedZipInputStream.of(new ByteArrayInputStream(zip), disabled)) {
            assertThat(archive).isNotInstanceOf(ProtectedZipInputStream.class);
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                archive.readAllBytes();
                count++;
            }
        }

        // Then no limit is enforced
        assertThat(count).isEqualTo(3);
    }

    @Test
    void shouldReturnPlainZipInputStreamWhenConfigNull() throws IOException {
        // Given a ZIP and a null config
        byte[] zip = zipOf("a", "b", "c");

        // When obtaining a stream
        try (ZipInputStream archive = ProtectedZipInputStream.of(new ByteArrayInputStream(zip), null)) {
            // Then a plain, unprotected ZipInputStream is returned
            assertThat(archive).isNotInstanceOf(ProtectedZipInputStream.class);
        }
    }
}
