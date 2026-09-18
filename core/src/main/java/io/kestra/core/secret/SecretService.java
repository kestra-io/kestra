package io.kestra.core.secret;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves secret values, on a worker as well as on any other server, so it must not depend on a bean a worker
 * lacks; listing secrets belongs to the webserver's {@code SecretListingService}.
 */
@Singleton
@Slf4j
public class SecretService {
    private static final String SECRET_PREFIX = "SECRET_";

    private final SecretEncoding encoding;

    private Map<String, String> decodedSecrets;

    @Inject
    public SecretService(final SecretConfiguration configuration) {
        this.encoding = configuration.encoding();
    }

    /** Kept so that secret backends subclassing this service are not forced to declare a constructor. */
    protected SecretService() {
        this.encoding = SecretEncoding.BASE64;
    }

    @PostConstruct
    private void postConstruct() {
        this.decode();
    }

    public void decode() {
        decodedSecrets = System.getenv().entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(SECRET_PREFIX)).<Map.Entry<String, String>> mapMulti((entry, consumer) ->
            {
                try {
                    consumer.accept(Map.entry(entry.getKey(), encoding.decode(entry.getValue())));
                } catch (Exception e) {
                    log.error("Could not decode secret '{}', make sure its value matches 'kestra.secret.encoding: {}': {}", entry.getKey(), encoding, e.getMessage());
                }
            })
            .collect(
                Collectors.toMap(
                    entry -> entry.getKey().substring(SECRET_PREFIX.length()).toUpperCase(),
                    Map.Entry::getValue
                )
            );
    }

    public String findSecret(String tenantId, String namespace, String key) throws SecretNotFoundException, IOException {
        String secret = decodedSecrets.get(key.toUpperCase());
        if (secret == null) {
            throw new SecretNotFoundException("Cannot find secret for key '" + key + "'.");
        }
        return secret;
    }

    /**
     * Finds the secret in full mode, as a value plus metadata.
     * The default returns the value with empty metadata. Multi-field secret managers override this to add metadata.
     */
    public SecretObject findSecretObject(String tenantId, String namespace, String key) throws SecretNotFoundException, IOException {
        return new SecretObject(findSecret(tenantId, namespace, key));
    }

    public Set<String> environmentSecretKeys() {
        return decodedSecrets.keySet();
    }
}
