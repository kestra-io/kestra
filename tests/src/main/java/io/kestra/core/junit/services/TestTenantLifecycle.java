package io.kestra.core.junit.services;

import jakarta.inject.Singleton;

/**
 * Test-only seam for provisioning the tenant a flow-loading fixture targets.
 * <p>
 * In OSS this is a no-op: there is no tenant repository, and the default/single tenant always exists.
 * Enterprise replaces this bean (via {@code @Replaces}) to actually create the tenant before a test
 * and delete it afterwards, so tenant-scoped requests against a freshly generated tenant don't fail
 * tenant-existence validation.
 * <p>
 * It is resolved by {@link io.kestra.core.junit.extensions.AbstractLoaderExtension} from the Micronaut
 * application context — which is the seam Enterprise overrides. (JUnit instantiates the extension class
 * directly from {@code @ExtendWith}, so {@code @Replaces} on the extension itself would never apply;
 * {@code @Replaces} only governs beans pulled from the context.)
 */
@Singleton
public class TestTenantLifecycle {

    /**
     * Ensure the given tenant exists before a fixture loads resources into it. No-op in OSS.
     *
     * @param tenantId the tenant identifier the fixture targets
     */
    public void create(String tenantId) {
        // no-op in OSS
    }

    /**
     * Remove a tenant previously created by {@link #create(String)}. No-op in OSS.
     *
     * @param tenantId the tenant identifier
     */
    public void delete(String tenantId) {
        // no-op in OSS
    }
}
