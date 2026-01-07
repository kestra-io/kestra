package io.kestra.core.repositories;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.repositories.ExecutionRepositoryInterface.ChildFilter;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.event.Level;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static io.kestra.core.models.flows.FlowScope.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(transactional = false)
public abstract class AbstractTriggerRepositoryTest {
    private static final String TEST_NAMESPACE = "io.kestra.unittest";

    @Inject
    protected TriggerRepositoryInterface triggerRepository;

    private static Trigger.TriggerBuilder<?, ?> trigger(String tenantId) {
        return Trigger.builder()
            .tenantId(tenantId)
            .flowId(IdUtils.create())
            .namespace(TEST_NAMESPACE)
            .triggerId(IdUtils.create())
            .executionId(IdUtils.create())
            .date(ZonedDateTime.now());
    }

    protected static Trigger generateDefaultTrigger(String tenantId){
        Trigger trigger = Trigger.builder()
            .tenantId(tenantId)
            .triggerId("triggerId")
            .namespace("trigger.namespace")
            .flowId("flowId")
            .nextExecutionDate(ZonedDateTime.now())
            .build();
        trigger.setWorkerId("workerId");
        return trigger;
    }

    @ParameterizedTest
    @MethodSource("filterCombinations")
    void should_find_all(QueryFilter filter){
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        triggerRepository.save(generateDefaultTrigger(tenant));

        ArrayListTotal<Trigger> entries = triggerRepository.find(Pageable.UNPAGED, tenant, List.of(filter));

        assertThat(entries).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("filterCombinations")
    void should_find_all_async(QueryFilter filter){
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        triggerRepository.save(generateDefaultTrigger(tenant));

        List<Trigger> entries = triggerRepository.findAsync(tenant, List.of(filter)).collectList().block();

        assertThat(entries).hasSize(1);
    }

    static Stream<QueryFilter> filterCombinations() {
        return Stream.of(
            QueryFilter.builder().field(Field.QUERY).value("flowId").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.SCOPE).value(List.of(USER)).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.NAMESPACE).value("trigger.namespace").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.FLOW_ID).value("flowId").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.START_DATE).value(ZonedDateTime.now().minusMinutes(1)).operation(Op.GREATER_THAN).build(),
            QueryFilter.builder().field(Field.END_DATE).value(ZonedDateTime.now().plusMinutes(1)).operation(Op.LESS_THAN).build(),
            QueryFilter.builder().field(Field.TRIGGER_ID).value("triggerId").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.WORKER_ID).value("workerId").operation(Op.EQUALS).build()
        );
    }

    @ParameterizedTest
    @MethodSource("errorFilterCombinations")
    void should_fail_to_find_all(QueryFilter filter){
        assertThrows(InvalidQueryFiltersException.class, () -> triggerRepository.find(Pageable.UNPAGED, TestsUtils.randomTenant(this.getClass().getSimpleName()), List.of(filter)));
    }

    static Stream<QueryFilter> errorFilterCombinations() {
        return Stream.of(
            QueryFilter.builder().field(Field.LABELS).value(Map.of("key", "value")).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.STATE).value(State.Type.RUNNING).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.TIME_RANGE).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.EXECUTION_ID).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.CHILD_FILTER).value(ChildFilter.CHILD).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.EXISTING_ONLY).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.MIN_LEVEL).value(Level.DEBUG).operation(Op.EQUALS).build()
        );
    }

    @Test
    void all() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Trigger.TriggerBuilder<?, ?> builder = trigger(tenant);

        Optional<Trigger> findLast = triggerRepository.findLast(builder.build());
        assertThat(findLast.isPresent()).isFalse();

        Trigger save = triggerRepository.save(builder.build());

        findLast = triggerRepository.findLast(save);

        assertThat(findLast.isPresent()).isTrue();
        assertThat(findLast.get().getExecutionId()).isEqualTo(save.getExecutionId());

        save = triggerRepository.save(builder.executionId(IdUtils.create()).build());

        findLast = triggerRepository.findLast(save);

        assertThat(findLast.isPresent()).isTrue();
        assertThat(findLast.get().getExecutionId()).isEqualTo(save.getExecutionId());


        triggerRepository.save(trigger(tenant).build());
        triggerRepository.save(trigger(tenant).build());
        Trigger searchedTrigger = trigger(tenant).build();
        triggerRepository.save(searchedTrigger);

        List<Trigger> all = triggerRepository.findAllForAllTenants();

        assertThat(all.size()).isGreaterThanOrEqualTo(4);

        all = triggerRepository.findAll(tenant);

        assertThat(all.size()).isEqualTo(4);

        String namespacePrefix = "io.kestra.another";
        String namespace = namespacePrefix + ".ns";
        Trigger trigger = trigger(tenant).namespace(namespace).build();
        triggerRepository.save(trigger);

        List<Trigger> find = triggerRepository.find(Pageable.from(1, 4, Sort.of(Sort.Order.asc("namespace"))), null, tenant, null, null, null);
        assertThat(find.size()).isEqualTo(4);
        assertThat(find.getFirst().getNamespace()).isEqualTo(namespace);

        find = triggerRepository.find(Pageable.from(1, 4, Sort.of(Sort.Order.asc("namespace"))), null, tenant, null, searchedTrigger.getFlowId(), null);
        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getFirst().getFlowId()).isEqualTo(searchedTrigger.getFlowId());

        find = triggerRepository.find(Pageable.from(1, 100, Sort.of(Sort.Order.asc(triggerRepository.sortMapping().apply("triggerId")))), null, tenant, namespacePrefix, null, null);
        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getFirst().getTriggerId()).isEqualTo(trigger.getTriggerId());

        // Full text search is on namespace, flowId, triggerId, executionId
        find = triggerRepository.find(Pageable.from(1, 100, Sort.UNSORTED), trigger.getNamespace(), tenant, null, null, null);
        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getFirst().getTriggerId()).isEqualTo(trigger.getTriggerId());
        find = triggerRepository.find(Pageable.from(1, 100, Sort.UNSORTED), searchedTrigger.getFlowId(), tenant, null, null, null);
        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getFirst().getTriggerId()).isEqualTo(searchedTrigger.getTriggerId());
        find = triggerRepository.find(Pageable.from(1, 100, Sort.UNSORTED), searchedTrigger.getTriggerId(), tenant, null, null, null);
        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getFirst().getTriggerId()).isEqualTo(searchedTrigger.getTriggerId());
        find = triggerRepository.find(Pageable.from(1, 100, Sort.UNSORTED), searchedTrigger.getExecutionId(), tenant, null, null, null);
        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getFirst().getTriggerId()).isEqualTo(searchedTrigger.getTriggerId());
    }

    @Test
    void shouldCountForNullTenant() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        triggerRepository.save(Trigger
            .builder()
            .tenantId(tenant)
            .triggerId(IdUtils.create())
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .build()
        );
        // When
        long count = triggerRepository.countAll(tenant);
        // Then
        assertThat(count).isEqualTo(1);
    }

    @Test
    void findAsync() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Trigger.TriggerBuilder<?, ?> builderA = trigger(tenant).flowId("flowA").triggerId("tA");
        Trigger.TriggerBuilder<?, ?> builderB = trigger(tenant).flowId("flowB").triggerId("tB");

        Trigger savedA = triggerRepository.save(builderA.build());
        Trigger savedB = triggerRepository.save(builderB.build());

        try {
            List<Trigger> all = triggerRepository.findAsync(tenant, null).collectList().block();
            assertThat(all).isNotNull();
            assertThat(all.stream().map(Trigger::getTriggerId).toList())
                .containsExactlyInAnyOrder(savedA.getTriggerId(), savedB.getTriggerId());

            List<QueryFilter> filters = List.of(QueryFilter.builder()
                .field(QueryFilter.Field.FLOW_ID)
                .operation(QueryFilter.Op.EQUALS)
                .value("flowA")
                .build());

            List<Trigger> filtered = triggerRepository.findAsync(tenant, filters).collectList().block();
            assertThat(filtered).hasSize(1);
            assertThat(filtered.get(0).getFlowId()).isEqualTo("flowA");
        } finally {
            triggerRepository.delete(savedA);
            triggerRepository.delete(savedB);
        }
    }
}
