package io.kestra.core.repositories;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.Label;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.repositories.ExecutionRepositoryInterface.ChildFilter;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.debug.Return;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.event.Level;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static io.kestra.core.models.flows.FlowScope.SYSTEM;
import static io.kestra.core.utils.NamespaceUtils.SYSTEM_FLOWS_DEFAULT_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(transactional = false)
public abstract class AbstractFlowRepositoryTest {
    public static final String TEST_NAMESPACE = "io.kestra.unittest";
    public static final String TEST_FLOW_ID = "test";
    @Inject
    protected FlowRepositoryInterface flowRepository;

    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    @BeforeAll
    protected static void init() {
        FlowListener.reset();
    }

    private static FlowWithSource.FlowWithSourceBuilder<?, ?> builder(String tenantId) {
        return builder(tenantId, IdUtils.create(), TEST_FLOW_ID);
    }

    private static FlowWithSource.FlowWithSourceBuilder<?, ?> builder(String tenantId, String flowId, String taskId) {
        return FlowWithSource.builder()
            .tenantId(tenantId)
            .id(flowId)
            .namespace(TEST_NAMESPACE)
            .tasks(Collections.singletonList(Return.builder().id(taskId).type(Return.class.getName()).format(Property.ofValue(TEST_FLOW_ID)).build()));
    }

    @ParameterizedTest
    @MethodSource("filterCombinations")
    void should_find_all(QueryFilter filter){
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow = FlowWithSource.builder()
            .id("filterFlowId")
            .namespace(SYSTEM_FLOWS_DEFAULT_NAMESPACE)
            .tenantId(tenant)
            .labels(Label.from(Map.of("key", "value")))
            .build();
        flow = flowRepository.create(GenericFlow.of(flow));
        try {
            ArrayListTotal<Flow> entries = flowRepository.find(Pageable.UNPAGED, tenant, List.of(filter));

            assertThat(entries).hasSize(1);
        } finally {
            deleteFlow(flow);
        }
    }

    @ParameterizedTest
    @MethodSource("filterCombinations")
    void should_find_all_with_source(QueryFilter filter){
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow = FlowWithSource.builder()
            .id("filterFlowId")
            .namespace(SYSTEM_FLOWS_DEFAULT_NAMESPACE)
            .tenantId(tenant)
            .labels(Label.from(Map.of("key", "value")))
            .build();
        flow = flowRepository.create(GenericFlow.of(flow));
        try {
            ArrayListTotal<FlowWithSource> entries = flowRepository.findWithSource(Pageable.UNPAGED, tenant, List.of(filter));

            assertThat(entries).hasSize(1);
        } finally {
            deleteFlow(flow);
        }
    }

    static Stream<QueryFilter> filterCombinations() {
        return Stream.of(
            QueryFilter.builder().field(Field.QUERY).value("filterFlowId").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.SCOPE).value(List.of(SYSTEM)).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.NAMESPACE).value(SYSTEM_FLOWS_DEFAULT_NAMESPACE).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.LABELS).value(Map.of("key", "value")).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.FLOW_ID).value("filterFlowId").operation(Op.EQUALS).build()
        );
    }

    @ParameterizedTest
    @MethodSource("errorFilterCombinations")
    void should_fail_to_find_all(QueryFilter filter){
        assertThrows(
            InvalidQueryFiltersException.class,
            () -> flowRepository.find(Pageable.UNPAGED, TestsUtils.randomTenant(this.getClass().getSimpleName()), List.of(filter)));

    }

    @ParameterizedTest
    @MethodSource("errorFilterCombinations")
    void should_fail_to_find_all_with_source(QueryFilter filter){
        assertThrows(
            InvalidQueryFiltersException.class,
            () -> flowRepository.findWithSource(Pageable.UNPAGED, TestsUtils.randomTenant(this.getClass().getSimpleName()), List.of(filter)));

    }

    static Stream<QueryFilter> errorFilterCombinations() {
        return Stream.of(
            QueryFilter.builder().field(Field.START_DATE).value(ZonedDateTime.now().minusMinutes(1)).operation(Op.GREATER_THAN).build(),
            QueryFilter.builder().field(Field.END_DATE).value(ZonedDateTime.now().plusMinutes(1)).operation(Op.LESS_THAN).build(),
            QueryFilter.builder().field(Field.STATE).value(State.Type.RUNNING).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.TIME_RANGE).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).value("executionTriggerId").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.TRIGGER_ID).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.EXECUTION_ID).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.CHILD_FILTER).value(ChildFilter.CHILD).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.WORKER_ID).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.EXISTING_ONLY).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.MIN_LEVEL).value(Level.DEBUG).operation(Op.EQUALS).build()
        );
    }

    @Test
    void findById() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow = builder(tenant)
            .revision(3)
            .build();
        flow = flowRepository.create(GenericFlow.of(flow));
        try {
            Optional<Flow> full = flowRepository.findById(tenant, flow.getNamespace(), flow.getId());
            assertThat(full.isPresent()).isTrue();
            assertThat(full.get().getRevision()).isEqualTo(1);

            full = flowRepository.findById(tenant, flow.getNamespace(), flow.getId(), Optional.empty());
            assertThat(full.isPresent()).isTrue();
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    void findByIdWithoutAcl() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow = builder(tenant)
            .tenantId(tenant)
            .revision(3)
            .build();
        flow = flowRepository.create(GenericFlow.of(flow));
        try {
            Optional<Flow> full = flowRepository.findByIdWithoutAcl(tenant, flow.getNamespace(), flow.getId(), Optional.empty());
            assertThat(full.isPresent()).isTrue();
            assertThat(full.get().getRevision()).isEqualTo(1);

            full = flowRepository.findByIdWithoutAcl(tenant, flow.getNamespace(), flow.getId(), Optional.empty());
            assertThat(full.isPresent()).isTrue();
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    void findByIdWithSource() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow = builder(tenant)
            .tenantId(tenant)
            .revision(3)
            .build();
        String source = "# comment\n" + flow.sourceOrGenerateIfNull();
        flow = flowRepository.create(GenericFlow.fromYaml(tenant, source));

        try {
            Optional<FlowWithSource> full = flowRepository.findByIdWithSource(tenant, flow.getNamespace(), flow.getId());
            assertThat(full.isPresent()).isTrue();

            full.ifPresent(current -> {
                assertThat(full.get().getRevision()).isEqualTo(1);
                assertThat(full.get().getSource()).contains("# comment");
                assertThat(full.get().getSource()).doesNotContain("revision:");
            });
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    void save() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow = builder(tenant).revision(12).build();
        FlowWithSource save = flowRepository.create(GenericFlow.of(flow));

        try {
            assertThat(save.getRevision()).isEqualTo(1);
        } finally {
            deleteFlow(save);
        }
    }

    @Test
    void saveNoRevision() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow = builder(tenant).build();
        FlowWithSource save = flowRepository.create(GenericFlow.of(flow));

        try {
            assertThat(save.getRevision()).isEqualTo(1);
        } finally {
            deleteFlow(save);
        }

    }

    @Test
    void findByNamespaceWithSource() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Flow flow = builder(tenant)
            .revision(3)
            .build();
        String flowSource = "# comment\n" + flow.sourceOrGenerateIfNull();
        flow = flowRepository.create(GenericFlow.fromYaml(tenant, flowSource));

        try {
            List<FlowWithSource> save = flowRepository.findByNamespaceWithSource(tenant, flow.getNamespace());
            assertThat((long) save.size()).isEqualTo(1L);

            assertThat(save.getFirst().getSource()).isEqualTo(FlowService.cleanupSource(flowSource));
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    void delete() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Flow flow = builder(tenant).tenantId(tenant).build();

        FlowWithSource save = flowRepository.create(GenericFlow.of(flow));

        try {
            assertThat(flowRepository.findById(tenant, save.getNamespace(), save.getId()).isPresent()).isTrue();
        } catch (Throwable e) {
            deleteFlow(save);
            throw e;
        }

        Flow delete = flowRepository.delete(save);

        assertThat(flowRepository.findById(tenant, flow.getNamespace(), flow.getId()).isPresent()).isFalse();
        assertThat(flowRepository.findById(tenant, flow.getNamespace(), flow.getId(), Optional.of(save.getRevision())).isPresent()).isTrue();

        List<FlowWithSource> revisions = flowRepository.findRevisions(tenant, flow.getNamespace(), flow.getId());
        assertThat(revisions.getLast().getRevision()).isEqualTo(delete.getRevision());
    }

    @Test
    void updateConflict() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String flowId = IdUtils.create();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace(TEST_NAMESPACE)
            .tenantId(tenant)
            .inputs(List.of(StringInput.builder().type(Type.STRING).id("a").build()))
            .tasks(Collections.singletonList(Return.builder().id(TEST_FLOW_ID).type(Return.class.getName()).format(Property.ofValue(TEST_FLOW_ID)).build()))
            .build();

        Flow save = flowRepository.create(GenericFlow.of(flow));

        try {
            assertThat(flowRepository.findById(tenant, flow.getNamespace(), flow.getId()).isPresent()).isTrue();

            Flow update = Flow.builder()
                .id(IdUtils.create())
                .namespace("io.kestra.unittest2")
                .tenantId(tenant)
                .inputs(List.of(StringInput.builder().type(Type.STRING).id("b").build()))
                .tasks(Collections.singletonList(Return.builder().id(TEST_FLOW_ID).type(Return.class.getName()).format(Property.ofValue(TEST_FLOW_ID)).build()))
                .build();
            ;

            ConstraintViolationException e = assertThrows(
                ConstraintViolationException.class,
                () -> flowRepository.update(GenericFlow.of(update), flow)
            );

            assertThat(e.getConstraintViolations().size()).isEqualTo(2);
        } finally {
            deleteFlow(save);
        }
    }

    @Test
    public void removeTrigger() throws TimeoutException {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String flowId = IdUtils.create();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace(TEST_NAMESPACE)
            .tenantId(tenant)
            .triggers(Collections.singletonList(UnitTest.builder()
                .id("sleep")
                .type(UnitTest.class.getName())
                .build()))
            .tasks(Collections.singletonList(Return.builder().id(TEST_FLOW_ID).type(Return.class.getName()).format(Property.ofValue(TEST_FLOW_ID)).build()))
            .build();

        flow = flowRepository.create(GenericFlow.of(flow));
        try {
            assertThat(flowRepository.findById(tenant, flow.getNamespace(), flow.getId()).isPresent()).isTrue();

            Flow update = Flow.builder()
                .id(flowId)
                .namespace(TEST_NAMESPACE)
                .tenantId(tenant)
                .tasks(Collections.singletonList(Return.builder().id(TEST_FLOW_ID).type(Return.class.getName()).format(Property.ofValue(TEST_FLOW_ID)).build()))
                .build();
            ;

            Flow updated = flowRepository.update(GenericFlow.of(update), flow);
            assertThat(updated.getTriggers()).isNull();
        } finally {
            deleteFlow(flow);
        }

        Await.until(() -> FlowListener.filterByTenant(tenant)
            .size() == 3, Duration.ofMillis(100), Duration.ofSeconds(5));
        assertThat(FlowListener.filterByTenant(tenant).stream()
            .filter(r -> r.getType() == CrudEventType.CREATE).count()).isEqualTo(1L);
        assertThat(FlowListener.filterByTenant(tenant).stream()
            .filter(r -> r.getType() == CrudEventType.UPDATE).count()).isEqualTo(1L);
        assertThat(FlowListener.filterByTenant(tenant).stream()
            .filter(r -> r.getType() == CrudEventType.DELETE).count()).isEqualTo(1L);
    }

    @Test
    void removeTriggerDelete() throws TimeoutException {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String flowId = IdUtils.create();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace(TEST_NAMESPACE)
            .tenantId(tenant)
            .triggers(Collections.singletonList(UnitTest.builder()
                .id("sleep")
                .type(UnitTest.class.getName())
                .build()))
            .tasks(Collections.singletonList(Return.builder().id(TEST_FLOW_ID).type(Return.class.getName()).format(Property.ofValue(TEST_FLOW_ID)).build()))
            .build();

        Flow save = flowRepository.create(GenericFlow.of(flow));
        try {
            assertThat(flowRepository.findById(tenant, flow.getNamespace(), flow.getId()).isPresent()).isTrue();
        } finally {
            deleteFlow(save);
        }

        Await.until(() -> FlowListener.filterByTenant(tenant)
            .size() == 2, Duration.ofMillis(100), Duration.ofSeconds(5));
        assertThat(FlowListener.filterByTenant(tenant).stream()
            .filter(r -> r.getType() == CrudEventType.CREATE).count()).isEqualTo(1L);
        assertThat(FlowListener.filterByTenant(tenant).stream()
            .filter(r -> r.getType() == CrudEventType.DELETE).count()).isEqualTo(1L);
    }


    @Test
    protected void shouldReturnNullRevisionForNonExistingFlow() {
        assertThat(flowRepository.lastRevision(TestsUtils.randomTenant(this.getClass().getSimpleName()), TEST_NAMESPACE, IdUtils.create())).isNull();
    }

    @Test
    protected void shouldReturnLastRevisionOnCreate() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        final List<Flow> toDelete = new ArrayList<>();
        final String flowId = IdUtils.create();
        try {
            // When
            toDelete.add(flowRepository.create(createTestingLogFlow(tenant, flowId, "???")));
            Integer result = flowRepository.lastRevision(tenant, TEST_NAMESPACE, flowId);

            // Then
            assertThat(result).isEqualTo(1);
            assertThat(flowRepository.lastRevision(tenant, TEST_NAMESPACE, flowId)).isEqualTo(1);
        } finally {
            toDelete.forEach(this::deleteFlow);
        }
    }

    @Test
    protected void shouldIncrementRevisionOnDelete() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        final String flowId = IdUtils.create();
        FlowWithSource created = flowRepository.create(createTestingLogFlow(tenant, flowId, "first"));
        assertThat(flowRepository.findRevisions(tenant, TEST_NAMESPACE, flowId).size()).isEqualTo(1);

        // When
        flowRepository.delete(created);

        // Then
        assertThat(flowRepository.findRevisions(tenant, TEST_NAMESPACE, flowId).size()).isEqualTo(2);
    }

    @Test
    protected void shouldIncrementRevisionOnCreateAfterDelete() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        final List<Flow> toDelete = new ArrayList<>();
        final String flowId = IdUtils.create();
        try {
            // Given
            flowRepository.delete(
                flowRepository.create(createTestingLogFlow(tenant, flowId, "first"))
            );

            // When
            toDelete.add(flowRepository.create(createTestingLogFlow(tenant, flowId, "second")));

            // Then
            assertThat(flowRepository.findRevisions(tenant, TEST_NAMESPACE, flowId).size()).isEqualTo(3);
            assertThat(flowRepository.lastRevision(tenant, TEST_NAMESPACE, flowId)).isEqualTo(3);
        } finally {
            toDelete.forEach(this::deleteFlow);
        }
    }

    @Test
    protected void shouldReturnNullForLastRevisionAfterDelete() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        final List<Flow> toDelete = new ArrayList<>();
        final String flowId = IdUtils.create();
        try {
            // Given
            FlowWithSource created = flowRepository.create(createTestingLogFlow(tenant, flowId, "first"));
            toDelete.add(created);

            FlowWithSource updated = flowRepository.update(createTestingLogFlow(tenant, flowId, "second"), created);
            toDelete.add(updated);

            // When
            flowRepository.delete(updated);

            // Then
            assertThat(flowRepository.findById(tenant, TEST_NAMESPACE, flowId, Optional.empty())).isEqualTo(Optional.empty());
            assertThat(flowRepository.lastRevision(tenant, TEST_NAMESPACE, flowId)).isNull();
        } finally {
            toDelete.forEach(this::deleteFlow);
        }
    }

    @Test
    protected void shouldFindAllRevisionsAfterDelete() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        final List<Flow> toDelete = new ArrayList<>();
        final String flowId = IdUtils.create();
        try {
            // Given
            FlowWithSource created = flowRepository.create(createTestingLogFlow(tenant, flowId, "first"));
            toDelete.add(created);

            FlowWithSource updated = flowRepository.update(createTestingLogFlow(tenant, flowId, "second"), created);
            toDelete.add(updated);

            // When
            flowRepository.delete(updated);

            // Then
            assertThat(flowRepository.findById(tenant, TEST_NAMESPACE, flowId, Optional.empty())).isEqualTo(Optional.empty());
            assertThat(flowRepository.findRevisions(tenant, TEST_NAMESPACE, flowId).size()).isEqualTo(3);
        } finally {
            toDelete.forEach(this::deleteFlow);
        }
    }

    @Test
    protected void shouldIncrementRevisionOnUpdateGivenNotEqualSource() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        final List<Flow> toDelete = new ArrayList<>();
        final String flowId = IdUtils.create();
        try {

            // Given
            FlowWithSource created = flowRepository.create(createTestingLogFlow(tenant, flowId, "first"));
            toDelete.add(created);

            // When
            FlowWithSource updated = flowRepository.update(createTestingLogFlow(tenant, flowId, "second"), created);
            toDelete.add(updated);

            // Then
            assertThat(updated.getRevision()).isEqualTo(2);
            assertThat(flowRepository.lastRevision(tenant, TEST_NAMESPACE, flowId)).isEqualTo(2);

        } finally {
            toDelete.forEach(this::deleteFlow);
        }
    }

    @Test
    protected void shouldNotIncrementRevisionOnUpdateGivenEqualSource() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        final List<Flow> toDelete = new ArrayList<>();
        final String flowId = IdUtils.create();
        try {

            // Given
            FlowWithSource created = flowRepository.create(createTestingLogFlow(tenant, flowId, "first"));
            toDelete.add(created);

            // When
            FlowWithSource updated = flowRepository.update(createTestingLogFlow(tenant, flowId, "first"), created);
            toDelete.add(updated);

            // Then
            assertThat(updated.getRevision()).isEqualTo(1);
            assertThat(flowRepository.lastRevision(tenant, TEST_NAMESPACE, flowId)).isEqualTo(1);

        } finally {
            toDelete.forEach(this::deleteFlow);
        }
    }

    @Test
    void findByExecution() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Flow flow = builder(tenant)
            .revision(1)
            .build();
        flowRepository.create(GenericFlow.of(flow));
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .tenantId(tenant)
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .state(new State())
            .build();
        execution = executionRepository.save(execution);

        try {
            Flow full = flowRepository.findByExecution(execution);
            assertThat(full).isNotNull();
            assertThat(full.getNamespace()).isEqualTo(flow.getNamespace());
            assertThat(full.getId()).isEqualTo(flow.getId());

            full = flowRepository.findByExecutionWithoutAcl(execution);
            assertThat(full).isNotNull();
            assertThat(full.getNamespace()).isEqualTo(flow.getNamespace());
            assertThat(full.getId()).isEqualTo(flow.getId());
        } finally {
            deleteFlow(flow);
            executionRepository.delete(execution);
        }
    }

    @Test
    void findByExecutionNoRevision() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Flow flow = builder(tenant)
            .revision(3)
            .build();
        flowRepository.create(GenericFlow.of(flow));
        Execution execution = Execution.builder()
            .tenantId(tenant)
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .state(new State())
            .build();
        executionRepository.save(execution);

        try {
            Flow full = flowRepository.findByExecution(execution);
            assertThat(full).isNotNull();
            assertThat(full.getNamespace()).isEqualTo(flow.getNamespace());
            assertThat(full.getId()).isEqualTo(flow.getId());

            full = flowRepository.findByExecutionWithoutAcl(execution);
            assertThat(full).isNotNull();
            assertThat(full.getNamespace()).isEqualTo(flow.getNamespace());
            assertThat(full.getId()).isEqualTo(flow.getId());
        } finally {
            deleteFlow(flow);
            executionRepository.delete(execution);
        }
    }

    @Test
    void shouldCountForNullTenant() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource toDelete = null;
        try {
            // Given
            Flow flow = createTestFlowForNamespace(tenant, TEST_NAMESPACE);
            toDelete = flowRepository.create(GenericFlow.of(flow));
            // When
            int count = flowRepository.count(tenant);

            // Then
            assertTrue(count > 0);
        } finally {
            Optional.ofNullable(toDelete).ifPresent(flow -> {
                flowRepository.delete(flow);
            });
        }
    }

    @Test
    void should_exist_for_tenant(){
        String tenantFlowExist = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flowExist = FlowWithSource.builder()
            .id("flowExist")
            .namespace(SYSTEM_FLOWS_DEFAULT_NAMESPACE)
            .tenantId(tenantFlowExist)
            .deleted(false)
            .build();
        flowExist = flowRepository.create(GenericFlow.of(flowExist));

        String tenantFlowDeleted = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flowDeleted = FlowWithSource.builder()
            .id("flowDeleted")
            .namespace(SYSTEM_FLOWS_DEFAULT_NAMESPACE)
            .tenantId(tenantFlowDeleted)
            .deleted(true)
            .build();
        flowDeleted = flowRepository.create(GenericFlow.of(flowDeleted));

        try {
            assertTrue(flowRepository.existAnyNoAcl(tenantFlowExist));
            assertFalse(flowRepository.existAnyNoAcl("not_found"));
            assertFalse(flowRepository.existAnyNoAcl(tenantFlowDeleted));
        } finally {
            deleteFlow(flowExist);
            deleteFlow(flowDeleted);
        }
    }

    @Test
    void findAsync() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        FlowWithSource flowA = builder(tenant, "flowA", "taskA").build();
        FlowWithSource flowB = builder(tenant, "flowB", "taskB").build();

        FlowWithSource savedA = flowRepository.create(GenericFlow.of(flowA));
        FlowWithSource savedB = flowRepository.create(GenericFlow.of(flowB));

        try {
            List<Flow> all = flowRepository.findAsync(tenant, null)
                .collectList()
                .block(Duration.ofSeconds(5));

            assertThat(all).isNotNull();
            assertThat(all.stream().map(Flow::getId).toList())
                .containsExactlyInAnyOrder(savedA.getId(), savedB.getId());

            // with a query filter targeting flowA -> only flowA
            QueryFilter filter = QueryFilter.builder()
                .field(Field.QUERY)
                .value(savedA.getId())
                .operation(Op.EQUALS)
                .build();

            List<Flow> filtered = flowRepository.findAsync(tenant, List.of(filter))
                .collectList()
                .block(Duration.ofSeconds(5));

            assertThat(filtered).isNotNull();
            assertThat(filtered).hasSize(1);
            assertThat(filtered.getFirst().getId()).isEqualTo(savedA.getId());
        } finally {
            deleteFlow(savedA);
            deleteFlow(savedB);
        }
    }



    private static Flow createTestFlowForNamespace(String tenantId, String namespace) {
        return Flow.builder()
            .id(IdUtils.create())
            .namespace(namespace)
            .tenantId(tenantId)
            .tasks(List.of(Return.builder()
                .id(IdUtils.create())
                .type(Return.class.getName())
                .build()
            ))
            .build();
    }

    protected void deleteFlow(Flow flow) {
        if (flow == null) {
            return;
        }
        flowRepository
            .findByIdWithSource(flow.getTenantId(), flow.getNamespace(), flow.getId())
            .ifPresent(delete -> flowRepository.delete(flow.toBuilder().revision(null).build()));
    }

    @Singleton
    public static class FlowListener implements ApplicationEventListener<CrudEvent<AbstractFlow>> {
        private static List<CrudEvent<AbstractFlow>> emits = new CopyOnWriteArrayList<>();

        @Override
        public void onApplicationEvent(CrudEvent<AbstractFlow> event) {
            //This has to be done because Micronaut may send CrudEvent<Setting> for example, and we don't want them.
            if ((event.getModel() != null && event.getModel() instanceof AbstractFlow)||
                (event.getPreviousModel() != null && event.getPreviousModel() instanceof AbstractFlow)) {
                emits.add(event);
            }
        }

        public static void reset() {
            emits = new CopyOnWriteArrayList<>();
        }

        public static List<CrudEvent<AbstractFlow>> filterByTenant(String tenantId){
            return emits.stream()
                .filter(e -> (e.getPreviousModel() != null && e.getPreviousModel().getTenantId().equals(tenantId)) ||
                    (e.getModel() != null && e.getModel().getTenantId().equals(tenantId)))
                .toList();
        }
    }

    private static GenericFlow createTestingLogFlow(String tenantId, String id, String logMessage) {
        String source = """
               id: %s
               namespace: %s
               tasks:
                 - id: log
                   type: io.kestra.plugin.core.log.Log
                   message: %s
            """.formatted(id, TEST_NAMESPACE, logMessage);
        return GenericFlow.fromYaml(tenantId, source);
    }

    protected static int COUNTER = 0;

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class UnitTest extends AbstractTrigger implements PollingTriggerInterface {
        @Builder.Default
        private final Duration interval = Duration.ofSeconds(2);

        private String defaultInjected;

        public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws InterruptedException {
            COUNTER++;

            if (COUNTER % 2 == 0) {
                Thread.sleep(4000);

                return Optional.empty();
            } else {
                Execution execution = Execution.builder()
                    .id(IdUtils.create())
                    .tenantId(context.getTenantId())
                    .namespace(context.getNamespace())
                    .flowId(context.getFlowId())
                    .flowRevision(conditionContext.getFlow().getRevision())
                    .state(new State())
                    .trigger(ExecutionTrigger.builder()
                        .id(this.getId())
                        .type(this.getType())
                        .variables(ImmutableMap.of(
                            "counter", COUNTER,
                            "defaultInjected", defaultInjected == null ? "ko" : defaultInjected
                        ))
                        .build()
                    )
                    .build();

                return Optional.of(execution);
            }
        }
    }
}
