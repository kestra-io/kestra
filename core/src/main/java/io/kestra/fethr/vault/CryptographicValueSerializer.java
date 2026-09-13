package io.kestra.fethr.vault;

import java.io.IOException;
import java.security.GeneralSecurityException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Writes a {@link CryptographicValue} as an encrypted string.
 *
 * <p>
 * The value is written as a bare JSON string rather than an object, so a stored secret or
 * credential holds {@code "value": "<ciphertext>"}. That is the shape the 1.x fork wrote, and
 * keeping it is what lets rows written before the 2.0 upgrade still be read after it.
 *
 * <p>
 * Attached to the type with {@code @JsonSerialize} rather than registered as a module on the shared
 * mapper. Every mapper then encrypts, including the one the JDBC repositories persist through,
 * without upstream's {@code JacksonMapper} having to be edited to know this type exists.
 */
public class CryptographicValueSerializer extends JsonSerializer<CryptographicValue> {

    @Override
    public void serialize(CryptographicValue value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        try {
            gen.writeString(VaultEncryption.encrypt(value.content()));
        } catch (GeneralSecurityException e) {
            throw new IOException("Unable to encrypt a vault value: " + e.getMessage(), e);
        }
    }
}
