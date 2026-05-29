package io.kestra.core.repositories;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.event.Level;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.repositories.ExecutionRepositoryInterface.ChildFilter;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.store.TriggerStateStore;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static io.kestra.core.models.flows.FlowScope.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(transactional = false)
public abstract class AbstractTriggerRepositoryTest {

    private static final String TEST_NAMESPACE = "io.kestra.unittest";
    private static final String ANY_VALUE = "???";
    private static final Pageable TEST_DEFAULT_PAGED = Pageable.from(1, 100, Sort.of(Sort.Order.asc("namespace")));

    @Inject
    protected TriggerStateStore triggerStateStore;

    @Inject
    protected TriggerRepositoryInterface triggerRepository;

    private static TriggerState.TriggerStateBuilder trigger(String tenantId) {
        return TriggerState.builder()
            .tenantId(tenantId)
            .flowId(IdUtils.create())
            .namespace(TEST_NAMESPACE)
            .triggerId(IdUtils.create())
            .evaluatedAt(Instant.now())
            .workerId("workerId");
    }

    protected static TriggerState generateDefaultTrigger(String tenantId) {
        return TriggerState.builder()
            .tenantId(tenantId)
            .triggerId("triggerId")
            .namespace("trigger.namespace")
            .flowId("flowId")
            .nextEvaluationDate(Instant.now())
            .workerId("workerId")
            .build();
    }

    @ParameterizedTest
    @MethodSource("filterCombinations")
    void should_find_all(QueryFilter filter) {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        triggerStateStore.save(generateDefaultTrigger(tenant));

        ArrayListTotal<TriggerState> entries = triggerRepository.find(Pageable.UNPAGED, tenant, List.of(filter));

        assertThat(entries).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("filterCombinations")
    void should_find_all_async(QueryFilter filter) {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        triggerStateStore.save(generateDefaultTrigger(tenant));

        List<TriggerState> entries = triggerRepository.find(tenant, List.of(filter)).collectList().block();

        assertThat(entries).withFailMessage(filter.toString()).hasSize(1);
    }

    static Stream<QueryFilter> filterCombinations() {
        return Stream.of(
            QueryFilter.builder().field(Field.QUERY).value("flowId").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.SCOPE).value(List.of(USER)).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.NAMESPACE).value("trigger.namespace").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.FLOW_ID).value("flowId").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.FLOW_ID).value(List.of("flowId")).operation(Op.IN).build(),
            QueryFilter.builder().field(Field.FLOW_ID).value(List.of("anotherFlowId")).operation(Op.NOT_IN).build(),
            QueryFilter.builder().field(Field.START_DATE).value(ZonedDateTime.now().minusMinutes(1)).operation(Op.GREATER_THAN).build(),
            QueryFilter.builder().field(Field.END_DATE).value(ZonedDateTime.now().plusMinutes(1)).operation(Op.LESS_THAN).build(),
            QueryFilter.builder().field(Field.TRIGGER_ID).value("triggerId").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.WORKER_ID).value("workerId").operation(Op.EQUALS).build()
        );
    }

    @ParameterizedTest
    @MethodSource("errorFilterCombinations")
    void should_fail_to_find_all(QueryFilter filter) {
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
            QueryFilter.builder().field(Field.LEVEL).value(Level.DEBUG).operation(Op.GREATER_THAN_OR_EQUAL_TO).build()
        );
    }

    @Test
    void shouldGetEmptyForFindGivenNoExistingId() {
        // GIVEN
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        TriggerState.TriggerStateBuilder builder = trigger(tenant);

        // WHEN
        Optional<TriggerState> result = triggerStateStore.findById(builder.build());

        // THEN
        assertThat(result).isEmpty();
    }

    @Test
    void shouldGetResultForFindGivenExistingId() {
        // GIVEN
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        TriggerState state = trigger(tenant).build();
        triggerStateStore.save(state);

        // WHEN
        Optional<TriggerState> result = triggerStateStore.findById(state);

        // THEN
        assertThat(result).isNotEmpty();
        assertThat(TriggerId.of(result.get())).isEqualTo(TriggerId.of(state));
    }

    @Test
    void shouldUpdateStateSavingExistingState() {
        // GIVEN
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        TriggerState.TriggerStateBuilder builder = trigger(tenant);

        triggerStateStore.save(builder.build());

        // WHEN
        Instant updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        TriggerState updated = builder.updatedAt(updatedAt).build();
        triggerStateStore.save(updated);

        // THEN
        Optional<TriggerState> result = triggerStateStore.findById(updated);
        assertThat(result).isNotEmpty();
        assertThat(TriggerId.of(result.get())).isEqualTo(TriggerId.of(updated));
        assertThat(result.get().getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void shouldFindAllForAllTenants() {
        // GIVEN
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant3 = TestsUtils.randomTenant(this.getClass().getSimpleName());

        triggerStateStore.save(trigger(tenant1).build());
        triggerStateStore.save(trigger(tenant2).build());
        triggerStateStore.save(trigger(tenant3).build());

        // WHEN
        List<TriggerState> all = triggerRepository.findAllForAllTenants()
            .stream().filter(it -> Set.of(tenant1, tenant2, tenant3).contains(it.getTenantId())).toList();

        // THEN
        assertThat(all).hasSize(3);
    }

    @Test
    void shouldFindAllForTenant() {
        // GIVEN
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant3 = TestsUtils.randomTenant(this.getClass().getSimpleName());

        triggerStateStore.save(trigger(tenant1).build());
        triggerStateStore.save(trigger(tenant2).build());
        triggerStateStore.save(trigger(tenant3).build());

        // WHEN
        List<TriggerState> all = triggerRepository.findAll(tenant1)
            .stream().filter(it -> Set.of(tenant1, tenant2, tenant3).contains(it.getTenantId())).toList();

        // THEN
        assertThat(all).hasSize(1);
    }

    @Test
    void shouldFindGivenTenant() {
        // GIVEN
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        triggerStateStore.save(trigger(tenant).triggerId("trigger1").build());
        triggerStateStore.save(trigger(tenant).triggerId("trigger2").build());
        triggerStateStore.save(trigger(tenant).triggerId("trigger3").build());
        triggerStateStore.save(trigger(tenant).triggerId("trigger4").build());

        // WHEN
        List<TriggerState> find = triggerRepository.find(TEST_DEFAULT_PAGED, null, tenant, null, null, null);

        // THEN
        assertThat(find.size()).isEqualTo(4);
    }

    @Test
    void shouldFindGivenFlowId() {
        // GIVEN
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        triggerStateStore.save(trigger(tenant).namespace("io.kestra.unittest.1").flowId("my-flow1").triggerId("trigger1").build());
        triggerStateStore.save(trigger(tenant).namespace("io.kestra.unittest.2").flowId("my-flow1").triggerId("trigger1").build());

        // WHEN
        List<TriggerState> find = triggerRepository.find(TEST_DEFAULT_PAGED, null, tenant, null, "my-flow1", null);

        // THEN
        assertThat(find.size()).isEqualTo(2);
    }

    @Test
    void shouldFindGivenNamespace() {
        // GIVEN
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        triggerStateStore.save(trigger(tenant).namespace("io.kestra.unittest.1").flowId("my-flow1").triggerId("trigger1").build());
        triggerStateStore.save(trigger(tenant).namespace("io.kestra.unittest.2").flowId("my-flow1").triggerId("trigger1").build());

        // WHEN
        List<TriggerState> find = triggerRepository.find(TEST_DEFAULT_PAGED, null, tenant, "io.kestra.unittest.1", null, null);

        // THEN
        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getFirst().getNamespace()).isEqualTo("io.kestra.unittest.1");
    }

    @Test
    void shouldFindGivenNamespacePrefix() {
        // GIVEN
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        triggerStateStore.save(trigger(tenant).namespace("io.kestra.unittest.1").flowId(ANY_VALUE).triggerId(ANY_VALUE).build());
        triggerStateStore.save(trigger(tenant).namespace("io.kestra.unittest.2").flowId(ANY_VALUE).triggerId(ANY_VALUE).build());
        triggerStateStore.save(trigger(tenant).namespace(ANY_VALUE).flowId(ANY_VALUE).triggerId(ANY_VALUE).build());

        // WHEN
        List<TriggerState> find = triggerRepository.find(TEST_DEFAULT_PAGED, null, tenant, "io.kestra", null, null);

        // THEN
        assertThat(find.size()).isEqualTo(2);
        assertThat(find.getFirst().getNamespace()).isEqualTo("io.kestra.unittest.1");
        assertThat(find.getLast().getNamespace()).isEqualTo("io.kestra.unittest.2");
    }

    @Test
    void shouldFindGivenWorkerId() {
        // GIVEN
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        triggerStateStore.save(trigger(tenant).namespace(ANY_VALUE).flowId(ANY_VALUE).workerId("worker1").build());
        triggerStateStore.save(trigger(tenant).namespace(ANY_VALUE).flowId(ANY_VALUE).workerId("worker2").build());
        triggerStateStore.save(trigger(tenant).namespace(ANY_VALUE).flowId(ANY_VALUE).triggerId(ANY_VALUE).build());

        // WHEN
        List<TriggerState> find = triggerRepository.find(TEST_DEFAULT_PAGED, null, tenant, null, null, "worker1");

        // THEN
        assertThat(find.size()).isEqualTo(1);
        assertThat(find.getFirst().getWorkerId()).isEqualTo("worker1");
    }

    @ParameterizedTest
    @ValueSource(strings = { "io.kestra.unittest1", "myflow1", "mytrigger1" })
    void shouldFindGivenFulltextSearchQuery(String query) {
        // GIVEN
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        triggerStateStore.save(trigger(tenant).namespace("io.kestra.unittest1").flowId("myflow1").triggerId("mytrigger1").build());
        triggerStateStore.save(trigger(tenant).namespace("io.kestra.unittest2").flowId("myflow2").triggerId("mytrigger2").build());

        // WHEN
        List<TriggerState> find = triggerRepository.find(TEST_DEFAULT_PAGED, query, tenant, null, null, null);

        // THEN
        assertThat(find.size()).isEqualTo(1);
    }

    @Test
    void shouldCountForNullTenant() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        triggerStateStore.save(
            TriggerState
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

        TriggerState.TriggerStateBuilder builderA = trigger(tenant).flowId("flowA").triggerId("tA");
        TriggerState.TriggerStateBuilder builderB = trigger(tenant).flowId("flowB").triggerId("tB");

        TriggerState savedA = triggerStateStore.save(builderA.build());
        TriggerState savedB = triggerStateStore.save(builderB.build());

        try {
            List<TriggerState> all = triggerRepository.find(tenant, null).collectList().block();
            assertThat(all).isNotNull();
            assertThat(all.stream().map(TriggerState::getTriggerId).toList())
                .containsExactlyInAnyOrder(savedA.getTriggerId(), savedB.getTriggerId());

            List<QueryFilter> filters = List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.FLOW_ID)
                    .operation(QueryFilter.Op.EQUALS)
                    .value("flowA")
                    .build()
            );

            List<TriggerState> filtered = triggerRepository.find(tenant, filters).collectList().block();
            assertThat(filtered).hasSize(1);
            assertThat(filtered.get(0).getFlowId()).isEqualTo("flowA");
        } finally {
            triggerStateStore.delete(savedA);
            triggerStateStore.delete(savedB);
        }
    }

    @Test
    void should_find_exact_prefix_suffix() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        TriggerState trigger = trigger(tenant).flowId("some_search_trigger").build();
        triggerStateStore.save(trigger);

        // exact match
        ArrayListTotal<TriggerState> entries = triggerRepository.find(
            Pageable.UNPAGED,
            tenant,
            List.of(QueryFilter.builder().field(Field.QUERY).value("some_search_trigger").operation(Op.EQUALS).build())
        );
        assertThat(entries).hasSize(1);

        // prefix match
        entries = triggerRepository.find(
            Pageable.UNPAGED,
            tenant,
            List.of(QueryFilter.builder().field(Field.QUERY).value("some_search").operation(Op.EQUALS).build())
        );
        assertThat(entries).hasSize(1);

        // suffix match
        entries = triggerRepository.find(
            Pageable.UNPAGED,
            tenant,
            List.of(QueryFilter.builder().field(Field.QUERY).value("search_trigger").operation(Op.EQUALS).build())
        );
        assertThat(entries).hasSize(1);

        // no match
        entries = triggerRepository.find(
            Pageable.UNPAGED,
            tenant,
            List.of(QueryFilter.builder().field(Field.QUERY).value("nothing").operation(Op.EQUALS).build())
        );
        assertThat(entries).hasSize(0);
    }

    @Test
    void shouldGetResultsForFindTriggersEligibleForSchedulingGivenNoExecutionDate() {
        // GIVEN
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        triggerStateStore.save(trigger(tenant1).triggerId("A").locked(false).vnode(0).nextEvaluationDate(null).build());
        triggerStateStore.save(trigger(tenant1).triggerId("B").locked(false).vnode(1).nextEvaluationDate(null).build());
        triggerStateStore.save(trigger(tenant2).triggerId("C").locked(false).vnode(2).nextEvaluationDate(null).build());
        // WHEN
        List<TriggerState> results = triggerStateStore.findTriggersEligibleForScheduling(ZonedDateTime.now(), Set.of(0, 1), false)
            .stream().filter(it -> Set.of(tenant1, tenant2).contains(it.getTenantId())).toList();

        // THEN
        assertThat(results.size()).isEqualTo(2);
        assertThat(results.stream().map(TriggerState::getTriggerId).toList()).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void shouldGetEmptyForFindTriggersEligibleForSchedulingGivenUnknownVNodes() {
        // GIVEN
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        triggerStateStore.save(trigger(tenant1).triggerId("A").locked(false).vnode(0).nextEvaluationDate(null).build());
        triggerStateStore.save(trigger(tenant2).triggerId("B").locked(false).vnode(1).nextEvaluationDate(null).build());
        triggerStateStore.save(trigger(tenant2).triggerId("C").locked(false).vnode(2).nextEvaluationDate(null).build());
        // WHEN
        List<TriggerState> results = triggerStateStore.findTriggersEligibleForScheduling(ZonedDateTime.now(), Set.of(3), false)
            .stream().filter(it -> Set.of(tenant1, tenant2).contains(it.getTenantId())).toList();

        // THEN
        assertThat(results.size()).isEqualTo(0);
    }

    @Test
    void shouldGetResultsForFindTriggersEligibleForSchedulingGivenLockedTrue() {
        // GIVEN
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        triggerStateStore.save(trigger(tenant1).triggerId("A").locked(false).vnode(0).nextEvaluationDate(null).build());
        triggerStateStore.save(trigger(tenant1).triggerId("B").locked(true).vnode(1).nextEvaluationDate(null).build());
        triggerStateStore.save(trigger(tenant2).triggerId("C").locked(false).vnode(2).nextEvaluationDate(null).build());
        // WHEN
        List<TriggerState> results = triggerStateStore.findTriggersEligibleForScheduling(ZonedDateTime.now(), Set.of(1), true)
            .stream().filter(it -> Set.of(tenant1, tenant2).contains(it.getTenantId())).toList();

        // THEN
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.stream().map(TriggerState::getTriggerId).toList()).containsExactlyInAnyOrder("B");
    }

    @Test
    void shouldGetResultsForFindTriggersEligibleForSchedulingGivenExecutionDate() {
        Instant now = Instant.now();
        // GIVEN
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        triggerStateStore.save(trigger(tenant1).triggerId("A").locked(false).vnode(0).nextEvaluationDate(now).build());
        triggerStateStore.save(trigger(tenant1).triggerId("B").locked(false).vnode(1).nextEvaluationDate(now.plus(Duration.ofMinutes(5))).build());
        triggerStateStore.save(trigger(tenant2).triggerId("C").locked(false).vnode(2).nextEvaluationDate(now.minus(Duration.ofMinutes(5))).build());
        triggerStateStore.save(trigger(tenant2).triggerId("D").locked(false).vnode(3).nextEvaluationDate(null).build());

        // WHEN
        List<TriggerState> results = triggerStateStore.findTriggersEligibleForScheduling(now.atZone(ZoneId.systemDefault()), Set.of(0, 1, 2, 3), false)
            .stream().filter(it -> Set.of(tenant1, tenant2).contains(it.getTenantId())).toList();

        // THEN
        assertThat(results.size()).isEqualTo(3);
        assertThat(results.stream().map(TriggerState::getTriggerId).toList()).containsExactlyInAnyOrder("A", "C", "D");
    }
}
