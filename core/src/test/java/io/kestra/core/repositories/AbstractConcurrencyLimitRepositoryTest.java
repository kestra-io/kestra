package io.kestra.core.repositories;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.ConcurrencyLimit;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public abstract class AbstractConcurrencyLimitRepositoryTest {
    @Inject
    private ConcurrencyLimitRepositoryInterface concurrencyLimitRepository;

    @Test
    void findById() {
        concurrencyLimitRepository.update(create("findById"));
        Optional<ConcurrencyLimit> limit = concurrencyLimitRepository.findById("tenant", "namespace", "findById");

        assertThat(limit).isNotEmpty();
        assertThat(limit.get().getRunning()).isEqualTo(1);
    }

    @Test
    void update() {
        ConcurrencyLimit concurrencyLimit = concurrencyLimitRepository.update(create("update"));

        ConcurrencyLimit updated =  concurrencyLimit.withRunning(99);
        concurrencyLimitRepository.update(updated);


        var limit = concurrencyLimitRepository.findById("tenant", "namespace", "update");
        assertThat(limit).isNotEmpty();
        assertThat(limit.get().getRunning()).isEqualTo(99);
    }

    @Test
    void list() {
        concurrencyLimitRepository.update(create("list1"));
        concurrencyLimitRepository.update(create("list2"));

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