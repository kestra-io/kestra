package io.kestra.fethr.credential;

import io.kestra.fethr.vault.CryptographicValue;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/**
 * A username and password pair.
 *
 * <p>
 * The username is stored as a {@link CryptographicValue} alongside the password, not as plain text:
 * for some providers the username is itself an account identifier worth protecting, and treating
 * both halves the same way keeps either from being logged by accident.
 */
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@Jacksonized
public class BasicAuthCredential extends Credential {

    @NotNull
    private CryptographicValue username;

    @NotNull
    private CryptographicValue password;

    protected BasicAuthCredential(BasicAuthCredentialBuilder<?, ?> builder) {
        super(builder);
        this.username = builder.username;
        this.password = builder.password;
        this.setType(CredentialType.BASIC_AUTH);
    }
    @Override
    public Credential toDeleted() {
        return markDeleted(this.toBuilder());
    }
}
