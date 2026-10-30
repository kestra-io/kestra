package io.kestra.core.repositories;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.runners.ConcurrencyLimitDataStore;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public abstract class AbstractConcurrencyLimitRepositoryTest {
    @Inject
    private ConcurrencyLimitRepositoryInterface concurrencyLimitRepository;

    @Inject
    private ConcurrencyLimitDataStore concurrencyLimitDataStore;

    @Test
    void findById() {
        concurrencyLimitDataStore.update(create("findById"));
        Optional<ConcurrencyLimit> limit = concurrencyLimitRepository.findById("tenant", "namespace", "findById");

        assertThat(limit).isNotEmpty();
        assertThat(limit.get().getRunning()).isEqualTo(1);
    }

    @Test
    void update() {
        ConcurrencyLimit concurrencyLimit = concurrencyLimitDataStore.update(create("update"));

        ConcurrencyLimit updated = concurrencyLimit.withRunning(99);
        concurrencyLimitDataStore.update(updated);

        var limit = concurrencyLimitRepository.findById("tenant", "namespace", "update");
        assertThat(limit).isNotEmpty();
        assertThat(limit.get().getRunning()).isEqualTo(99);
    }

    @Test
    void delete() {
        ConcurrencyLimit concurrencyLimit = concurrencyLimitDataStore.update(create("delete"));

        concurrencyLimitDataStore.delete(concurrencyLimit);

        var limit = concurrencyLimitRepository.findById("tenant", "namespace", "delete");
        assertThat(limit).isEmpty();
    }

    @Test
    void list() {
        concurrencyLimitDataStore.update(create("list1"));
        concurrencyLimitDataStore.update(create("list2"));

        List<ConcurrencyLimit> list = concurrencyLimitRepository.find("tenant");

        // depending on the order of the test, previous tests may have created concurrency limit records
        assertThat(list).hasSizeGreaterThanOrEqualTo(2);
    }

    private ConcurrencyLimit create(String flow) {
        return ConcurrencyLimit.builder()
            .tenantId("tenant")
            .namespace("namespace")
            .flowId(flow)
            .running(1)
            .build();
    }
}