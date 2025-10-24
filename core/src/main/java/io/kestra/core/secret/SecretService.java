package io.kestra.core.secret;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ArrayListTotal;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class SecretService<META> {
    private static final String SECRET_PREFIX = "SECRET_";

    private Map<String, String> decodedSecrets;


    @PostConstruct
    private void postConstruct() {
        this.decode();
    }

    public void decode() {
        decodedSecrets = System.getenv().entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(SECRET_PREFIX))
            .<Map.Entry<String, String>>mapMulti((entry, consumer) -> {
                try {
                    String value = entry.getValue().replaceAll("\\R", "");
                    consumer.accept(Map.entry(entry.getKey(), new String(Base64.getDecoder().decode(value))));
                } catch (Exception e) {
                    log.error("Could not decode secret '{}', make sure it is Base64-encoded: {}", entry.getKey(), e.getMessage());
                }
            })
            .collect(Collectors.toMap(
                entry -> entry.getKey().substring(SECRET_PREFIX.length()).toUpperCase(),
                Map.Entry::getValue
            ));
    }

    public String findSecret(String tenantId, String namespace, String key) throws SecretNotFoundException, IOException {
        String secret = decodedSecrets.get(key.toUpperCase());
        if (secret == null) {
            throw new SecretNotFoundException("Cannot find secret for key '" + key + "'.");
        }
        return secret;
    }

    public ArrayListTotal<META> list(Pageable pageable, String tenantId, List<QueryFilter> filters) throws IOException {
        final Predicate<String> queryPredicate = filters.stream()
            .filter(filter -> filter.field().equals(QueryFilter.Field.QUERY) && filter.value() != null)
            .findFirst()
            .map(filter -> {
                if (filter.operation().equals(QueryFilter.Op.EQUALS)) {
                    return (Predicate<String>) s -> Strings.CI.contains(s, (String) filter.value());
                } else if (filter.operation().equals(QueryFilter.Op.NOT_EQUALS)) {
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
}
