package io.kestra.core.runners.pebble.functions;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

import io.kestra.core.encryption.EncryptionService;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

public class DecryptFunction implements KestraFunction {
    public static final String NAME = "decrypt";
    @Override
    public List<String> getArgumentNames() {
        return List.of("key", "encrypted");
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        return Map.of(
            "key", SecretFunction.NAME + "('encryption_key')",
            "encrypted", "outputs.request.encryptedBody"
        );
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey("key") || !args.containsKey("encrypted")) {
            throw new PebbleException(null, "The 'decrypt' function expects two arguments 'key' and 'encrypted'.", lineNumber, self.getName());
        }

        String key = (String) args.get("key");
        String encrypted = (String) args.get("encrypted");
        try {
            return EncryptionService.decrypt(key, encrypted);
        } catch (GeneralSecurityException e) {
            throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
        }
    }
}
