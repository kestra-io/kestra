package io.kestra.core.models.tasks.common;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.security.GeneralSecurityException;

@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true)
@Schema(hidden = true)
public class EncryptedString {
    private final String value;

    private EncryptedString(String value) {
        this.value = value;
    }

    public static EncryptedString from(String plainText, RunContext runContext) throws GeneralSecurityException {
        String encrypted = runContext.encrypt(plainText);
        return new EncryptedString(encrypted);
    }
}
