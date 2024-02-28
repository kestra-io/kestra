package io.kestra.core.models.tasks.common;

import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.security.GeneralSecurityException;

@Getter
@Schema(hidden = true)
public class EncryptedString {

    public static final String TYPE = "io.kestra.datatype:aes_encrypted";

    private final String value;

    private final String type = TYPE;

    private EncryptedString(String value) {
        this.value = value;
    }

    public static EncryptedString from(String encrypted) {
        return new EncryptedString(encrypted);
    }

    public static EncryptedString from(String plainText, RunContext runContext) throws GeneralSecurityException {
        String encrypted = runContext.encrypt(plainText);
        return new EncryptedString(encrypted);
    }
}
