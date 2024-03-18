package io.kestra.core.encryption;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class EncryptionServiceTest {
    private static final String KEY = "I6EGNzRESu3X3pKZidrqCGOHQFUFC0yK";

    @Test
    public void avoidNpeForEmptyOrNullText() throws GeneralSecurityException {
        assertThat(EncryptionService.encrypt(KEY, null), nullValue());
        assertThat(EncryptionService.decrypt(KEY, null), nullValue());
        assertThat(EncryptionService.encrypt(KEY, ""), is(""));
        assertThat(EncryptionService.decrypt(KEY, ""), is(""));
    }
}
