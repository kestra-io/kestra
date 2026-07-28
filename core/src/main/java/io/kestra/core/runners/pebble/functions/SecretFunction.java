package io.kestra.core.runners.pebble.functions;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.runners.RunVariables;
import io.kestra.core.secret.SecretException;
import io.kestra.core.secret.SecretNotFoundException;
import io.kestra.core.secret.SecretObject;
import io.kestra.core.secret.SecretService;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.NamespaceService;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SecretFunction implements Function {
    public static final String NAME = "secret";

    private static final ObjectMapper OBJECT_MAPPER = JacksonMapper.ofJson();
    private static final String SUBKEY_ARG = "subkey";
    private static final String NAMESPACE_ARG = "namespace";
    private static final String KEY_ARG = "key";
    private static final String FULL_ARG = "full";
    private static final String VALUE_KEY = "value";
    private static final String METADATA_KEY = "metadata";

    @Inject
    private SecretService secretService;

    @Inject
    private NamespaceService namespaceService;

    @Override
    public List<String> getArgumentNames() {
        return List.of(KEY_ARG, NAMESPACE_ARG, SUBKEY_ARG, FULL_ARG);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        String key = getSecretKey(args, self, lineNumber);
        String namespace = (String) args.get(NAMESPACE_ARG);

        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        String flowNamespace = flow.get(NAMESPACE_ARG);
        String flowTenantId = flow.get("tenantId");

        if (namespace == null) {
            namespace = flowNamespace;
        } else {
            namespaceService.checkAllowedNamespace(flowTenantId, namespace, flowTenantId, flowNamespace);
        }

        final String subkey = (String) args.get(SUBKEY_ARG);
        final boolean full = Boolean.TRUE.equals(args.get(FULL_ARG));

        if (full && subkey != null && !subkey.isEmpty()) {
            throw new PebbleException(null, "The 'secret' function cannot be called with both 'subkey' and 'full' arguments.", lineNumber, self.getName());
        }

        try {
            if (full) {
                SecretObject secretObject = secretService.findSecretObject(flowTenantId, namespace, key);
                consumeSecret(context, secretObject.value());

                Map<String, Object> result = new LinkedHashMap<>();
                result.put(VALUE_KEY, secretObject.value());
                if (!secretObject.metadata().isEmpty()) {
                    secretObject.metadata().values().forEach(value -> consumeSecret(context, value));
                    result.put(METADATA_KEY, secretObject.metadata());
                }
                return result;
            }

            String secret = secretService.findSecret(flowTenantId, namespace, key);

            if (subkey != null && !subkey.isEmpty()) {
                try {
                    JsonNode subkeys = OBJECT_MAPPER.readTree(secret);
                    if (!subkeys.has(subkey)) {
                        throw new SecretNotFoundException("Cannot find secret sub-key '" + subkey + "' in secret '" + key + "'.");
                    } else {
                        JsonNode jsonNode = subkeys.get(subkey);
                        secret = jsonNode.isValueNode() ? jsonNode.asText() : jsonNode.toString();
                    }
                } catch (JsonProcessingException e) {
                    throw new SecretException(
                        String.format(
                            "Failed to read secret sub-key '%s' from secret '%s'. Ensure the secret contains valid JSON value.",
                            subkey,
                            key
                        )
                    );
                }
            }

            consumeSecret(context, secret);
            return secret;
        } catch (SecretException | IOException e) {
            throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
        }
    }

    @SuppressWarnings("unchecked")
    private void consumeSecret(EvaluationContext context, String value) {
        try {
            Consumer<String> addSecretConsumer = (Consumer<String>) context.getVariable(RunVariables.SECRET_CONSUMER_VARIABLE_NAME);
            addSecretConsumer.accept(value);
        } catch (Exception e) {
            log.warn("Unable to get secret consumer", e);
        }
    }

    protected String getSecretKey(Map<String, Object> args, PebbleTemplate self, int lineNumber) {
        if (!args.containsKey(KEY_ARG)) {
            throw new PebbleException(null, "The 'secret' function expects an argument 'key'.", lineNumber, self.getName());
        }

        return (String) args.get(KEY_ARG);
    }
}
