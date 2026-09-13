package io.kestra.fethr.credential;

import io.kestra.fethr.vault.CryptographicValue;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/** A pre-issued bearer token, used as-is. Nothing here refreshes it. */
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@Jacksonized
public class BearerTokenCredential extends Credential {

    @NotNull
    private CryptographicValue bearerToken;

    protected BearerTokenCredential(BearerTokenCredentialBuilder<?, ?> builder) {
        super(builder);
        this.bearerToken = builder.bearerToken;
        this.setType(CredentialType.BEARER_TOKEN);
    }
    @Override
    public Credential toDeleted() {
        return markDeleted(this.toBuilder());
    }
}
