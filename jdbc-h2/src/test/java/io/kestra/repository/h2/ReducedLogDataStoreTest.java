package io.kestra.repository.h2;

import io.kestra.core.repositories.AbstractLogDataStoreTest;
import io.kestra.core.repositories.LogDataStoreInterface;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

/**
 * Runs the shared {@link AbstractLogDataStoreTest} contract against {@link ReducedLogDataStore} — the
 * reduced-capability profile of a cloud log store (canAggregate / canPurge = false, cursor pagination).
 * <p>
 * The shared suite branches on the store's declared capabilities, so this single subclass exercises the
 * degraded paths (aggregate → empty, purge → no-op, cursor pagination) as well as the find/write paths —
 * no bespoke tests are needed here.
 * <p>
 * Wired via {@code @MockBean} (with {@code kestra.logs.type} unset so it falls back to the H2 repository,
 * keeping the log-table migrations running) — which makes the suite order-independent.
 */
// A distinct environment forces Micronaut to give this class its own application context, so its
// @MockBean(LogDataStoreInterface) does not bleed into other suites (e.g. H2LogDataStoreTest) via the
// shared @MicronautTest context cache. logs.type stays unset, so it still falls back to H2.
@MicronautTest(transactional = false, environments = "reduced-fixture")
class ReducedLogDataStoreTest extends AbstractLogDataStoreTest {

    @MockBean(LogDataStoreInterface.class)
    LogDataStoreInterface reducedLogStore(ApplicationContext applicationContext) {
        ReducedLogDataStore store = new ReducedLogDataStore();
        store.init(applicationContext);
        return store;
    }
}
