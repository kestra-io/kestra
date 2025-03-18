package io.kestra.core.repositories;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.utils.IdUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public abstract class AbstractTriggerRepositoryTest {
    private static final String TEST_NAMESPACE = "io.kestra.unittest";

    @Inject
    protected TriggerRepositoryInterface triggerRepository;

    private static Trigger.TriggerBuilder<?, ?> trigger() {
        return Trigger.builder()
            .flowId(IdUtils.create())
            .namespace(TEST_NAMESPACE)
            .triggerId(IdUtils.create())
            .executionId(IdUtils.create())
            .date(ZonedDateTime.now());
    }

    @Test
    void all() {
        Trigger.TriggerBuilder<?, ?> builder = trigger();

        Optional<Trigger> findLast = triggerRepository.findLast(builder.build());
        assertThat(findLast.isPresent(), is(false));

        Trigger save = triggerRepository.save(builder.build());

        findLast = triggerRepository.findLast(save);

        assertThat(findLast.isPresent(), is(true));
        assertThat(findLast.get().getExecutionId(), is(save.getExecutionId()));

        save = triggerRepository.save(builder.executionId(IdUtils.create()).build());

        findLast = triggerRepository.findLast(save);

        assertThat(findLast.isPresent(), is(true));
        assertThat(findLast.get().getExecutionId(), is(save.getExecutionId()));


        triggerRepository.save(trigger().build());
        triggerRepository.save(trigger().build());
        Trigger searchedTrigger = trigger().build();
        triggerRepository.save(searchedTrigger);

        List<Trigger> all = triggerRepository.findAllForAllTenants();

        assertThat(all.size(), is(4));

        all = triggerRepository.findAll(null);

        assertThat(all.size(), is(4));

        String namespacePrefix = "io.kestra.another";
        String namespace = namespacePrefix + ".ns";
        Trigger trigger = trigger().namespace(namespace).build();
        triggerRepository.save(trigger);

        List<Trigger> find = triggerRepository.find(Pageable.from(1, 4, Sort.of(Sort.Order.asc("namespace"))), null, null, null, null, null);
        assertThat(find.size(), is(4));
        assertThat(find.getFirst().getNamespace(), is(namespace));

        find = triggerRepository.find(Pageable.from(1, 4, Sort.of(Sort.Order.asc("namespace"))), null, null, null, searchedTrigger.getFlowId(), null);
        assertThat(find.size(), is(1));
        assertThat(find.getFirst().getFlowId(), is(searchedTrigger.getFlowId()));

        find = triggerRepository.find(Pageable.from(1, 100, Sort.of(Sort.Order.asc(triggerRepository.sortMapping().apply("triggerId")))), null, null, namespacePrefix, null, null);
        assertThat(find.size(), is(1));
        assertThat(find.getFirst().getTriggerId(), is(trigger.getTriggerId()));

        // Full text search is on namespace, flowId, triggerId, executionId
        find = triggerRepository.find(Pageable.from(1, 100, Sort.UNSORTED), trigger.getNamespace(), null, null, null, null);
        assertThat(find.size(), is(1));
        assertThat(find.getFirst().getTriggerId(), is(trigger.getTriggerId()));
        find = triggerRepository.find(Pageable.from(1, 100, Sort.UNSORTED), searchedTrigger.getFlowId(), null, null, null, null);
        assertThat(find.size(), is(1));
        assertThat(find.getFirst().getTriggerId(), is(searchedTrigger.getTriggerId()));
        find = triggerRepository.find(Pageable.from(1, 100, Sort.UNSORTED), searchedTrigger.getTriggerId(), null, null, null, null);
        assertThat(find.size(), is(1));
        assertThat(find.getFirst().getTriggerId(), is(searchedTrigger.getTriggerId()));
        find = triggerRepository.find(Pageable.from(1, 100, Sort.UNSORTED), searchedTrigger.getExecutionId(), null, null, null, null);
        assertThat(find.size(), is(1));
        assertThat(find.getFirst().getTriggerId(), is(searchedTrigger.getTriggerId()));
    }

    @Test
    void shouldCountForNullTenant() {
        // Given
        triggerRepository.save(Trigger
            .builder()
            .triggerId(IdUtils.create())
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .build()
        );
        // When
        int count = triggerRepository.count(null);
        // Then
        assertThat(count, is(1));
    }

    @Test
    void shouldCountForNullTenantGivenNamespace() {
        // Given
        triggerRepository.save(Trigger
            .builder()
            .triggerId(IdUtils.create())
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest.p2")
            .build()
        );

        triggerRepository.save(Trigger
            .builder()
            .triggerId(IdUtils.create())
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest.shouldcountbynamespacefornulltenant")
            .build()
        );

        triggerRepository.save(Trigger
            .builder()
            .triggerId(IdUtils.create())
            .flowId(IdUtils.create())
            .namespace("com.kestra.unittest")
            .build()
        );

        // When
        int count = triggerRepository.countForNamespace(null, "io.kestra.unittest.shouldcountbynamespacefornulltenant");
        assertThat(count, is(1));

        count = triggerRepository.countForNamespace(null, "io.kestra.unittest");
        assertThat(count, is(2));
    }
}
