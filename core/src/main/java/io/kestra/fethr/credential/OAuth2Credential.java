package io.kestra.fethr.credential;

import java.util.List;

import io.kestra.fethr.vault.CryptographicValue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/** OAuth2 client credentials, exchanged at {@link #tokenUrl} for an access token at run time. */
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@Jacksonized
public class OAuth2Credential extends Credential {

    @NotBlank
    private String tokenUrl;

    @NotNull
    private CryptographicValue tenantKey;

    @NotNull
    private CryptographicValue clientId;

    @NotNull
    private CryptographicValue clientSecret;

    private List<String> scopes;

    protected OAuth2Credential(OAuth2CredentialBuilder<?, ?> builder) {
        super(builder);
        this.tokenUrl = builder.tokenUrl;
        this.tenantKey = builder.tenantKey;
        this.clientId = builder.clientId;
        this.clientSecret = builder.clientSecret;
        this.scopes = builder.scopes;
        this.setType(CredentialType.OAUTH2);
    }
    @Override
    public Credential toDeleted() {
        return markDeleted(this.toBuilder());
    }
}
