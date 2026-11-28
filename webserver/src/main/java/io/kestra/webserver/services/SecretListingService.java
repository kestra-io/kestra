package io.kestra.webserver.services;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.commons.lang3.Strings;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.secret.SecretService;

import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Lists the secrets shown to a user; a worker resolves values through {@link SecretService} and never has this class.
 */
@Singleton
public class SecretListingService<META> {
    private final SecretService secretService;

    @Inject
    public SecretListingService(SecretService secretService) {
        this.secretService = secretService;
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
            secretService.environmentSecretKeys().stream().filter(queryPredicate).map(s -> (META) s).toList()
        );
    }

    public Map<String, Set<String>> listInheritedSecretKeys(String tenantId, String namespace) throws IOException {
        return Map.of(namespace, secretService.environmentSecretKeys());
    }

    public Map<String, Set<String>> listSecretKeys(String tenantId, String namespace) throws IOException {
        return listInheritedSecretKeys(tenantId, namespace);
    }
}
