package io.kestra.fethr.vault;

import java.io.IOException;
import java.security.GeneralSecurityException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * Reads an encrypted string back into a {@link CryptographicValue}.
 *
 * @see CryptographicValueSerializer
 */
public class CryptographicValueDeserializer extends JsonDeserializer<CryptographicValue> {

    @Override
    public CryptographicValue deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        try {
            return new CryptographicValue(VaultEncryption.decrypt(parser.getValueAsString()));
        } catch (GeneralSecurityException e) {
            throw new IOException("Unable to decrypt a vault value: " + e.getMessage(), e);
        }
    }
}
