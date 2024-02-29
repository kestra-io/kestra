package io.kestra.core.runners.pebble.functions;

import io.kestra.core.encryption.EncryptionService;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

public class EncryptFunction implements Function {
    @Override
    public List<String> getArgumentNames() {
        return List.of("key", "plaintext");
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
        }
        catch (GeneralSecurityException e) {
            throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
        }
    }
}
