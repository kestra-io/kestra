package io.kestra.fethr.secret;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.kestra.core.secret.SecretNotFoundException;
import io.kestra.core.secret.SecretObject;
import io.kestra.core.secret.SecretService;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Resolves secrets from Kestra's own database instead of from {@code SECRET_*} environment
 * variables.
 *
 * <p>
 * Resolution is an <strong>exact namespace match</strong>. A secret stored in {@code fethr} is not
 * visible to a flow in {@code fethr.hl7}; nothing walks up the namespace tree.
 *
 * <p>
 * The 1.x fork did walk it, in {@code lookupSecretWithInheritance}, but nothing could reach a
 * stored secret that way: its secrets API pinned every row to the {@code system} namespace, and an
 * inheritance chain built from {@code NamespaceUtils.asTree} never yields {@code system} for a flow
 * that does not already live under it. Only an explicit {@code secret(namespace='system', …)}
 * resolved a stored secret, which exact matching serves identically. Dropping the walk therefore
 * changes no reachable behaviour and removes a resolution path that was quietly dead.
 *
 * <p>
 * Environment secrets still resolve: this falls back to {@link SecretService}'s decoded
 * {@code SECRET_*} map when the database holds nothing for the key, so an operator-supplied secret
 * keeps working alongside the stored ones.
 */
@Singleton
@Replaces(SecretService.class)
public class FethrSecretService extends SecretService<String> {

    private final SecretRepositoryInterface secretRepository;

    @Inject
    public FethrSecretService(SecretRepositoryInterface secretRepository) {
        this.secretRepository = secretRepository;
    }

    @Override
    public String findSecret(String tenantId, String namespace, String key) throws SecretNotFoundException, IOException {
        return secretRepository.findByKey(tenantId, namespace, key)
            .map(Secret::getValue)
            .map(CryptographicValue::content)
            .orElseGet(() -> findEnvironmentSecret(tenantId, namespace, key));
    }

    @Override
    public SecretObject findSecretObject(String tenantId, String namespace, String key) throws SecretNotFoundException, IOException {
        return new SecretObject(findSecret(tenantId, namespace, key));
    }

    /**
     * The keys a namespace can see. With exact matching that is exactly the namespace's own keys,
     * so the map has a single entry -- kept in the inherited-secrets shape the API and UI expect.
     */
    @Override
    public Map<String, Set<String>> inheritedSecrets(String tenantId, String namespace) throws IOException {
        Set<String> keys = secretRepository.findByNamespace(tenantId, namespace).stream()
            .map(Secret::getKey)
            .collect(Collectors.toCollection(TreeSet::new));

        return Map.of(namespace, keys);
    }

    /**
     * Delegates to the superclass so a {@code SECRET_*} environment variable still resolves, and
     * rethrows its checked absence unchecked so this can sit in an {@code orElseGet}.
     */
    private String findEnvironmentSecret(String tenantId, String namespace, String key) {
        try {
            return super.findSecret(tenantId, namespace, key);
        } catch (SecretNotFoundException | IOException e) {
            throw new SecretNotFoundException(
                "Cannot find secret for key '" + key + "' in namespace '" + namespace + "'."
            );
        }
    }
}
