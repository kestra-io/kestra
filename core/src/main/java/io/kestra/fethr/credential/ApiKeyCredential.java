package io.kestra.fethr.credential;

import io.kestra.fethr.vault.CryptographicValue;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/** A single opaque API key, sent however the calling task decides to send it. */
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@Jacksonized
public class ApiKeyCredential extends Credential {

    @NotNull
    private CryptographicValue key;

    protected ApiKeyCredential(ApiKeyCredentialBuilder<?, ?> builder) {
        super(builder);
        this.key = builder.key;
        this.setType(CredentialType.API_KEY);
    }

    @Override
    public Credential toDeleted() {
        return markDeleted(this.toBuilder());
    }
}
