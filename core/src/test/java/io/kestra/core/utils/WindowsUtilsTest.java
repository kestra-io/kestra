package io.kestra.core.utils;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsUtilsTest {

    @Test
    void shouldLowerCaseDriveLetterUsingRootLocaleUnderTurkishLocale() {
        // Given: a JVM whose default locale lower-cases ASCII 'I' to the dotless 'ı'
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.of("tr", "TR"));

        try {
            // When: converting an I-drive Windows path
            String unixPath = WindowsUtils.windowsToUnixPath("I:\\namespace\\file.txt");

            // Then: the drive letter is the plain ASCII 'i', not the dotless 'ı' (U+0131)
            assertThat(unixPath).isEqualTo("/i/namespace/file.txt");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
