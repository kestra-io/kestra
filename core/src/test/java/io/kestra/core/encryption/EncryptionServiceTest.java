package io.kestra.core.encryption;

import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;

import static org.assertj.core.api.Assertions.assertThat;

public class EncryptionServiceTest {
    private static final String KEY = "I6EGNzRESu3X3pKZidrqCGOHQFUFC0yK";

    @Test
    void encryptAndDecryptString() throws GeneralSecurityException {
        String text = "Hello World!";
        String encrypted = EncryptionService.encrypt(KEY, text);
        String decrypted = EncryptionService.decrypt(KEY, encrypted);
        assertThat(decrypted).isEqualTo(text);
    }

    @Test
    void encryptAndDecryptByteArray() throws GeneralSecurityException {
        byte[] text = "Hello World!".getBytes();
        byte[] encrypted = EncryptionService.encrypt(KEY, text);
        byte[] decrypted = EncryptionService.decrypt(KEY, encrypted);
        assertThat(new String(decrypted)).isEqualTo("Hello World!");
    }

    @Test
    void avoidNpeForEmptyOrNullText() throws GeneralSecurityException {
        assertThat(EncryptionService.encrypt(KEY, (String) null)).isNull();
        assertThat(EncryptionService.decrypt(KEY, (String) null)).isNull();
        assertThat(EncryptionService.encrypt(KEY, (byte[]) null)).isNull();
        assertThat(EncryptionService.decrypt(KEY, (byte[]) null)).isNull();
        assertThat(EncryptionService.encrypt(KEY, "")).isEqualTo("");
        assertThat(EncryptionService.decrypt(KEY, "")).isEqualTo("");
    }
}
