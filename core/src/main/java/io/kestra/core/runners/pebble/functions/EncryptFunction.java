package io.kestra.core.runners.pebble.functions;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

import io.kestra.core.encryption.EncryptionService;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

public class EncryptFunction implements KestraFunction {
    public static final String NAME = "encrypt";
    @Override
    public List<String> getArgumentNames() {
        return List.of("key", "plaintext");
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        return Map.of(
            "key", SecretFunction.NAME + "('encryption_key')",
            "plaintext", "'value_to_encrypt'"
        );
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey("key") || !args.containsKey("plaintext")) {
            throw new PebbleException(null, "The 'encrypt' function expects two arguments 'key' and 'plaintext'.", lineNumber, self.getName());
        }

        String key = (String) args.get("key");
        String plaintext = (String) args.get("plaintext");
        try {
            return EncryptionService.encrypt(key, plaintext);
        } catch (GeneralSecurityException e) {
            throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
        }
    }
}
