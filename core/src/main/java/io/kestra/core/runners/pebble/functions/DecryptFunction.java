package io.kestra.core.runners.pebble.functions;

import io.kestra.core.crypto.CryptoService;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

@Singleton
public class DecryptFunction implements Function {
    @Inject
    private CryptoService cryptoService;

    @Override
    public List<String> getArgumentNames() {
        return List.of("string");
    }
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey("string")) {
            throw new PebbleException(null, "The 'decrypt' function expects an argument 'string'.", lineNumber, self.getName());
        }

        String s = (String) args.get("string");
        try {
            return cryptoService.decrypt(s);
        }
        catch (GeneralSecurityException e) {
            throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
        }
    }
}
