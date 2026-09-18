package io.kestra.core.secret;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Strings;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ArrayListTotal;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class SecretService<META> {
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

    public ArrayListTotal<META> list(Pageable pageable, String tenantId, List<QueryFilter> filters) throws IOException {
        final Predicate<String> queryPredicate = filters.stream()
            .filter(filter -> QueryFilter.Field.QUERY.equals(filter.field()) && filter.value() != null)
            .findFirst()
            .map(filter ->
            {
                if (QueryFilter.Op.EQUALS.equals(filter.operation())) {
                    return (Predicate<String>) s -> Strings.CI.contains(s, (String) filter.value());
                } else if (QueryFilter.Op.NOT_EQUALS.equals(filter.operation())) {
                    return (Predicate<String>) s -> !Strings.CI.contains(s, (String) filter.value());
                } else {
                    throw new IllegalArgumentException("Unsupported operation for QUERY filter: " + filter.operation());
                }
            })
            .orElse(s -> true);

        //noinspection unchecked
        return ArrayListTotal.of(
            pageable,
            decodedSecrets.keySet().stream().filter(queryPredicate).map(s -> (META) s).toList()
        );
    }

    public Map<String, Set<String>> inheritedSecrets(String tenantId, String namespace) throws IOException {
        return Map.of(namespace, decodedSecrets.keySet());
    }

    public Map<String, Set<String>> ownAndInheritedSecrets(String tenantId, String namespace) throws IOException {
        return inheritedSecrets(tenantId, namespace);
    }
}
