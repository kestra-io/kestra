package io.kestra.fethr.credential;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import io.kestra.core.runners.RunVariables;
import io.kestra.core.runners.pebble.functions.KestraFunction;
import io.kestra.core.secret.SecretNotFoundException;
import io.kestra.core.services.NamespaceService;
import io.kestra.fethr.vault.CryptographicValue;

import io.micronaut.context.annotation.Requires;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * The pebble {@code credential(key, namespace?, subkey?)} function.
 *
 * <p>
 * Resolves a stored credential for use in flow YAML, typically as an {@code Authorization} header:
 *
 * <pre>{@code
 * Authorization: Bearer {{ credential('stripe') }}
 * Authorization: Basic  {{ credential('jenkins') }}
 * X-Client-Id:   {{ credential('oauth_app', subkey='clientId') }}
 * }</pre>
 *
 * <p>
 * Without {@code subkey} it renders the credential's natural access string: the key for an API key,
 * the token for a bearer token, the base64 {@code user:password} tuple for basic auth, the client
 * secret for the OAuth2-shaped types. With {@code subkey} it returns one named field.
 *
 * <p>
 * <strong>Namespace resolution differs from secrets here, deliberately.</strong> With no explicit
 * namespace, this walks the flow's parent tree ({@code a.b.c} then {@code a.b} then {@code a}) and
 * the nearest ancestor wins, so a credential on a parent namespace is inherited. Secrets resolve by
 * exact match instead -- but secrets were pinned to a single namespace no flow's chain could reach,
 * so nothing was relying on their inheritance. Credentials have always been stored in real, chosen
 * namespaces, so flows can and do depend on inheriting them; removing it would break them.
 *
 * <p>
 * Whatever is returned is fed to the run context's secret consumer, so it is masked out of logs.
 */
@Slf4j
@Singleton
@Requires(beans = CredentialRepositoryInterface.class)
public class CredentialFunction implements KestraFunction {

    public static final String NAME = "credential";

    private static final String KEY_ARG = "key";
    private static final String NAMESPACE_ARG = "namespace";
    private static final String SUBKEY_ARG = "subkey";

    @Inject
    private CredentialRepositoryInterface credentialRepository;

    @Inject
    private Provider<NamespaceService> namespaceService;

    @Override
    public List<String> getArgumentNames() {
        return List.of(KEY_ARG, NAMESPACE_ARG, SUBKEY_ARG);
    }

    /**
     * Autocompletion defaults. The namespace default is the flow's own, which is also what an
     * omitted namespace resolves from -- except that omitting it inherits up the tree, where
     * spelling it out does not.
     */
    @Override
    public Map<String, String> getArgumentDefaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put(KEY_ARG, "'MY_CREDENTIAL'");
        defaults.put(NAMESPACE_ARG, "flow.namespace");
        defaults.put(SUBKEY_ARG, null);
        return defaults;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey(KEY_ARG)) {
            throw new PebbleException(null, "The 'credential' function expects an argument 'key'.", lineNumber, self.getName());
        }

        String key = (String) args.get(KEY_ARG);
        String namespace = (String) args.get(NAMESPACE_ARG);

        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        String flowNamespace = flow.get(NAMESPACE_ARG);
        String flowTenantId = flow.get("tenantId");

        Optional<Credential> found;
        if (namespace == null) {
            found = findWithInheritance(flowTenantId, flowNamespace, key);
            namespace = flowNamespace;
        } else {
            namespaceService.get().checkAllowedNamespace(flowTenantId, namespace, flowTenantId, flowNamespace);
            found = credentialRepository.findByName(flowTenantId, namespace, key);
        }

        Credential credential = found.orElseThrow(() -> new PebbleException(
            null,
            "Cannot find credential '" + key + "' in namespace '" + flow.get(NAMESPACE_ARG) + "'.",
            lineNumber,
            self.getName()
        ));

        String subkey = (String) args.get(SUBKEY_ARG);
        String value = subkey == null || subkey.isEmpty()
            ? accessString(credential)
            : subkey(credential, subkey, key, self, lineNumber);

        consume(context, value);
        return value;
    }

    /**
     * The credential's natural access string: what a caller almost always wants in an
     * {@code Authorization} header without having to name a field.
     */
    private static String accessString(Credential credential) {
        return switch (credential) {
            case ApiKeyCredential c -> c.getKey().content();
            case BearerTokenCredential c -> c.getBearerToken().content();
            case BasicAuthCredential c -> Base64.getEncoder().encodeToString(
                "%s:%s".formatted(c.getUsername().content(), c.getPassword().content()).getBytes()
            );
            case OAuth2Credential c -> c.getClientSecret().content();
            case CernerFhirCredential c -> c.getClientSecret().content();
            default -> throw new IllegalArgumentException(
                "Unknown credential type '" + credential.getClass().getName() + "'"
            );
        };
    }

    private static String subkey(Credential credential, String subkey, String key, PebbleTemplate self, int lineNumber) {
        Object value = fields(credential).get(subkey);

        if (value == null) {
            throw new PebbleException(
                new SecretNotFoundException("Cannot find credential sub-key '" + subkey + "' in credential '" + key + "'."),
                "Cannot find credential sub-key '" + subkey + "' in credential '" + key + "'.",
                lineNumber,
                self.getName()
            );
        }

        return value instanceof String s ? s : String.valueOf(value);
    }

    /**
     * The addressable fields of a credential, built explicitly per type.
     *
     * <p>
     * The 1.x fork produced this by serializing the credential through a private mapper whose
     * {@code CryptographicValue} serializer had been swapped out, because the shared one encrypts.
     * Naming the fields directly avoids that -- there is no mapper to keep in step with the
     * encrypting one, and no risk of a future field leaking in simply by existing.
     */
    private static Map<String, Object> fields(Credential credential) {
        Map<String, Object> fields = new LinkedHashMap<>();

        switch (credential) {
            case ApiKeyCredential c -> put(fields, "key", c.getKey());
            case BearerTokenCredential c -> put(fields, "bearerToken", c.getBearerToken());
            case BasicAuthCredential c -> {
                put(fields, "username", c.getUsername());
                put(fields, "password", c.getPassword());
            }
            case OAuth2Credential c -> {
                fields.put("tokenUrl", c.getTokenUrl());
                put(fields, "tenantKey", c.getTenantKey());
                put(fields, "clientId", c.getClientId());
                put(fields, "clientSecret", c.getClientSecret());
                fields.put("scopes", c.getScopes());
            }
            case CernerFhirCredential c -> {
                put(fields, "tenantKey", c.getTenantKey());
                put(fields, "clientId", c.getClientId());
                put(fields, "clientSecret", c.getClientSecret());
                fields.put("scopes", c.getScopes());
                fields.put("fhirVersion", c.getFhirVersion());
                fields.put("sandbox", c.isSandbox());
                fields.put("tokenUrl", c.getTokenUrl());
                fields.put("fhirBaseUrl", c.getFhirBaseUrl());
                fields.put("conformance", c.getConformance());
            }
            default -> throw new IllegalArgumentException(
                "Unknown credential type '" + credential.getClass().getName() + "'"
            );
        }

        fields.values().removeIf(java.util.Objects::isNull);
        return fields;
    }

    private static void put(Map<String, Object> fields, String name, CryptographicValue value) {
        fields.put(name, value == null ? null : value.content());
    }

    /**
     * Walks the flow's parent namespace tree, nearest first, and takes the first match.
     */
    private Optional<Credential> findWithInheritance(String tenantId, String flowNamespace, String key) {
        for (String namespace : namespaceInheritanceChain(flowNamespace)) {
            Optional<Credential> credential = credentialRepository.findByName(tenantId, namespace, key);
            if (credential.isPresent()) {
                return credential;
            }
        }
        return Optional.empty();
    }

    /**
     * The lookup chain for inheritance, nearest first: {@code a.b.c} yields
     * {@code [a.b.c, a.b, a]}.
     */
    static List<String> namespaceInheritanceChain(String namespace) {
        List<String> chain = new ArrayList<>();
        String current = namespace;
        chain.add(current);
        while (current.contains(".")) {
            current = current.substring(0, current.lastIndexOf('.'));
            chain.add(current);
        }
        return chain;
    }

    /**
     * Hands the resolved value to the run context's secret consumer so it is masked from logs.
     *
     * <p>
     * Reuses the secret consumer rather than adding a credential-specific one: the fork declared a
     * second variable, and it pointed at the same {@code logger::usedSecret}.
     */
    @SuppressWarnings("unchecked")
    private static void consume(EvaluationContext context, String value) {
        try {
            Consumer<String> consumer = (Consumer<String>) context.getVariable(RunVariables.SECRET_CONSUMER_VARIABLE_NAME);
            if (consumer != null) {
                consumer.accept(value);
            }
        } catch (Exception e) {
            log.warn("Unable to reach the secret consumer, a credential value will not be masked from logs", e);
        }
    }
}
