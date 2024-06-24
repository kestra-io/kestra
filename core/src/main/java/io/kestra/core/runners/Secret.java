package io.kestra.core.runners;

import io.kestra.core.encryption.EncryptionService;
import io.kestra.core.models.tasks.common.EncryptedString;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;

import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

final class Secret {

    private final Optional<String> secretKey;
    private final  Supplier<Logger> logger;

    Secret(final Optional<String> secretKey, final Supplier<Logger> logger) {
        this.secretKey = Objects.requireNonNull(secretKey, "secretKey cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    String decrypt(final String encrypted) throws GeneralSecurityException {
        if (secretKey.isPresent()) {
            return EncryptionService.decrypt(secretKey.get(), encrypted);
        } else {
            logger.get().warn("Unable to decrypt the output as encryption is not configured");
            return encrypted;
        }
    }

    String encrypt(final String plaintext) throws GeneralSecurityException {
        if (secretKey.isPresent()) {
            return EncryptionService.encrypt(secretKey.get(), plaintext);
        } else {
            logger.get().warn("Unable to encrypt the output as encryption is not configured");
            return plaintext;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Map<String, Object> decrypt(final Map<String, Object> data) {
        Map<String, Object> decryptedMap = new HashMap<>(data);
        for (var entry: data.entrySet()) {
            if (entry.getValue() instanceof Map map) {
                // if some value are of type EncryptedString we decode them and replace the object
                if (EncryptedString.TYPE.equalsIgnoreCase((String)map.get("type"))) {
                    try {
                        String decoded = decrypt((String) map.get("value"));
                        decryptedMap.put(entry.getKey(), decoded);
                    } catch (GeneralSecurityException e) {
                        throw new RuntimeException(e);
                    }
                }  else {
                    decryptedMap.put(entry.getKey(), decrypt((Map<String, Object>) map));
                }
            }
        }
        return decryptedMap;
    }
}
