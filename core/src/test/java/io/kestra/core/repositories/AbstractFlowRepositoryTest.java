package io.kestra.core.repositories;

import io.kestra.core.Helpers;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.schedulers.AbstractSchedulerTest;
import io.kestra.core.services.FlowService;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.flow.Template;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import jakarta.validation.ConstraintViolationException;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

// If some counts are wrong in this test it means that one of the tests is not properly deleting what it created
@KestraTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractFlowRepositoryTest {
    public static final String TEST_TENANT_ID = "tenant";
    public static final String TEST_NAMESPACE = "io.kestra.unittest";
    public static final String TEST_FLOW_ID = "test";
    @Inject
    protected FlowRepositoryInterface flowRepository;

    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    @Inject
    private LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        TestsUtils.loads(MAIN_TENANT, repositoryLoader);
        FlowListener.reset();
    }

    private static FlowWithSource.FlowWithSourceBuilder<?, ?> builder() {
        return builder(IdUtils.create(), TEST_FLOW_ID);
    }

    private static FlowWithSource.FlowWithSourceBuilder<?, ?> builder(String flowId, String taskId) {
        return FlowWithSource.builder()
            .id(flowId)
            .namespace(TEST_NAMESPACE)
            .tasks(Collections.singletonList(Return.builder().id(taskId).type(Return.class.getName()).format(Property.of(TEST_FLOW_ID)).build()));
    }

    @Test
    void findById() {
        FlowWithSource flow = builder()
            .tenantId(MAIN_TENANT)
            .revision(3)
            .build();
        flow = flowRepository.create(GenericFlow.of(flow));
        try {
            Optional<Flow> full = flowRepository.findById(MAIN_TENANT, flow.getNamespace(), flow.getId());
            assertThat(full.isPresent()).isTrue();
            assertThat(full.get().getRevision()).isEqualTo(1);

            full = flowRepository.findById(MAIN_TENANT, flow.getNamespace(), flow.getId(), Optional.empty());
            assertThat(full.isPresent()).isTrue();
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    void findByIdWithoutAcl() {
        FlowWithSource flow = builder()
            .tenantId(MAIN_TENANT)
            .revision(3)
            .build();
        flow = flowRepository.create(GenericFlow.of(flow));
        try {
            Optional<Flow> full = flowRepository.findByIdWithoutAcl(MAIN_TENANT, flow.getNamespace(), flow.getId(), Optional.empty());
            assertThat(full.isPresent()).isTrue();
            assertThat(full.get().getRevision()).isEqualTo(1);

            full = flowRepository.findByIdWithoutAcl(MAIN_TENANT, flow.getNamespace(), flow.getId(), Optional.empty());
            assertThat(full.isPresent()).isTrue();
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    void findByIdWithSource() {
        FlowWithSource flow = builder()
            .tenantId(MAIN_TENANT)
            .revision(3)
            .build();
        String source = "# comment\n" + flow.sourceOrGenerateIfNull();
        flow = flowRepository.create(GenericFlow.fromYaml(MAIN_TENANT, source));

        try {
            Optional<FlowWithSource> full = flowRepository.findByIdWithSource(MAIN_TENANT, flow.getNamespace(), flow.getId());
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
        FlowWithSource flow = builder().revision(12).build();
        FlowWithSource save = flowRepository.create(GenericFlow.of(flow));

        try {
            assertThat(save.getRevision()).isEqualTo(1);
        } finally {
            deleteFlow(save);
        }
    }

    @Test
    void saveNoRevision() {
        FlowWithSource flow = builder().build();
        FlowWithSource save = flowRepository.create(GenericFlow.of(flow));

        try {
            assertThat(save.getRevision()).isEqualTo(1);
        } finally {
            deleteFlow(save);
        }

    }

    @Test
    void findAll() {
        List<Flow> save = flowRepository.findAll(MAIN_TENANT);

        assertThat((long) save.size()).isEqualTo(Helpers.FLOWS_COUNT);
    }

    @Test
    void findAllWithSource() {
        List<FlowWithSource> save = flowRepository.findAllWithSource(MAIN_TENANT);

        assertThat((long) save.size()).isEqualTo(Helpers.FLOWS_COUNT);
    }

    @Test
    void findAllForAllTenants() {
        List<Flow> save = flowRepository.findAllForAllTenants();

        assertThat((long) save.size()).isEqualTo(Helpers.FLOWS_COUNT);
    }

    @Test
    void findAllWithSourceForAllTenants() {
        List<FlowWithSource> save = flowRepository.findAllWithSourceForAllTenants();

        assertThat((long) save.size()).isEqualTo(Helpers.FLOWS_COUNT);
    }

    @Test
    void findByNamespace() {
        List<Flow> save = flowRepository.findByNamespace(MAIN_TENANT, "io.kestra.tests");
        assertThat((long) save.size()).isEqualTo(Helpers.FLOWS_COUNT - 22);

        save = flowRepository.findByNamespace(MAIN_TENANT, "io.kestra.tests2");
        assertThat((long) save.size()).isEqualTo(1L);

        save = flowRepository.findByNamespace(MAIN_TENANT, "io.kestra.tests.minimal.bis");
        assertThat((long) save.size()).isEqualTo(1L);
    }

    @Test
    void findByNamespacePrefix() {
        List<Flow> save = flowRepository.findByNamespacePrefix(MAIN_TENANT, "io.kestra.tests");
        assertThat((long) save.size()).isEqualTo(Helpers.FLOWS_COUNT - 1);

        save = flowRepository.findByNamespace(MAIN_TENANT, "io.kestra.tests2");
        assertThat((long) save.size()).isEqualTo(1L);

        save = flowRepository.findByNamespace(MAIN_TENANT, "io.kestra.tests.minimal.bis");
        assertThat((long) save.size()).isEqualTo(1L);
    }

    @Test
    void findByNamespaceWithSource() {
        Flow flow = builder()
            .revision(3)
            .build();
        String flowSource = "# comment\n" + flow.sourceOrGenerateIfNull();
        flow = flowRepository.create(GenericFlow.fromYaml(MAIN_TENANT, flowSource));

        try {
            List<FlowWithSource> save = flowRepository.findByNamespaceWithSource(MAIN_TENANT, flow.getNamespace());
            assertThat((long) save.size()).isEqualTo(1L);

            assertThat(save.getFirst().getSource()).isEqualTo(FlowService.cleanupSource(flowSource));
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    protected void find() {
        List<Flow> save = flowRepository.find(Pageable.from(1, (int) Helpers.FLOWS_COUNT - 1, Sort.UNSORTED), null, MAIN_TENANT, null, null, null);
        assertThat((long) save.size()).isEqualTo(Helpers.FLOWS_COUNT - 1);

        save = flowRepository.find(Pageable.from(1, (int) Helpers.FLOWS_COUNT + 1, Sort.UNSORTED), null, MAIN_TENANT, null, null, null);
        assertThat((long) save.size()).isEqualTo(Helpers.FLOWS_COUNT);

        save = flowRepository.find(Pageable.from(1), null, MAIN_TENANT, null, "io.kestra.tests.minimal.bis", Collections.emptyMap());
        assertThat((long) save.size()).isEqualTo(1L);

        save = flowRepository.find(Pageable.from(1, 100, Sort.UNSORTED), null, MAIN_TENANT, null, null, Map.of("country", "FR"));
        assertThat(save.size()).isEqualTo(1);

        save = flowRepository.find(Pageable.from(1), null, MAIN_TENANT, null, "io.kestra.tests", Map.of("key2", "value2"));
        assertThat((long) save.size()).isEqualTo(1L);

        save = flowRepository.find(Pageable.from(1), null, MAIN_TENANT, null, "io.kestra.tests", Map.of("key1", "value2"));
        assertThat((long) save.size()).isEqualTo(0L);
    }

    @Test
    protected void findSpecialChars() {
        ArrayListTotal<SearchResult<Flow>> save = flowRepository.findSourceCode(Pageable.unpaged(), "https://api.chucknorris.io", MAIN_TENANT, null);
        assertThat((long) save.size()).isEqualTo(2L);
    }

    @Test
    void findWithSource() {
        List<FlowWithSource> save = flowRepository.findWithSource(null, MAIN_TENANT, null, "io.kestra.tests", Collections.emptyMap());
        assertThat((long) save.size()).isEqualTo(Helpers.FLOWS_COUNT - 1);

        save = flowRepository.findWithSource(null, MAIN_TENANT, null, "io.kestra.tests2", Collections.emptyMap());
        assertThat((long) save.size()).isEqualTo(1L);

        save = flowRepository.findWithSource(null, MAIN_TENANT, null, "io.kestra.tests.minimal.bis", Collections.emptyMap());
        assertThat((long) save.size()).isEqualTo(1L);
    }

    @Test
    void delete() {
        Flow flow = builder().tenantId(MAIN_TENANT).build();

        FlowWithSource save = flowRepository.create(GenericFlow.of(flow));

        try {
            assertThat(flowRepository.findById(MAIN_TENANT, save.getNamespace(), save.getId()).isPresent()).isTrue();
        } catch (Throwable e) {
            deleteFlow(save);
            throw e;
        }

        Flow delete = flowRepository.delete(save);

        assertThat(flowRepository.findById(MAIN_TENANT, flow.getNamespace(), flow.getId()).isPresent()).isFalse();
        assertThat(flowRepository.findById(MAIN_TENANT, flow.getNamespace(), flow.getId(), Optional.of(save.getRevision())).isPresent()).isTrue();

        List<FlowWithSource> revisions = flowRepository.findRevisions(MAIN_TENANT, flow.getNamespace(), flow.getId());
        assertThat(revisions.getLast().getRevision()).isEqualTo(delete.getRevision());
    }

    @Test
    void updateConflict() {
        String flowId = IdUtils.create();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace(TEST_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .inputs(List.of(StringInput.builder().type(Type.STRING).id("a").build()))
            .tasks(Collections.singletonList(Return.builder().id(TEST_FLOW_ID).type(Return.class.getName()).format(Property.of(TEST_FLOW_ID)).build()))
            .build();

        Flow save = flowRepository.create(GenericFlow.of(flow));

        try {
            assertThat(flowRepository.findById(MAIN_TENANT, flow.getNamespace(), flow.getId()).isPresent()).isTrue();

            Flow update = Flow.builder()
                .id(IdUtils.create())
                .namespace("io.kestra.unittest2")
                .tenantId(MAIN_TENANT)
                .inputs(List.of(StringInput.builder().type(Type.STRING).id("b").build()))
                .tasks(Collections.singletonList(Return.builder().id(TEST_FLOW_ID).type(Return.class.getName()).format(Property.of(TEST_FLOW_ID)).build()))
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
    void removeTrigger() throws TimeoutException, QueueException {
        String flowId = IdUtils.create();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace(TEST_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .triggers(Collections.singletonList(AbstractSchedulerTest.UnitTest.builder()
                .id("sleep")
                .type(AbstractSchedulerTest.UnitTest.class.getName())
                .build()))
            .tasks(Collections.singletonList(Return.builder().id(TEST_FLOW_ID).type(Return.class.getName()).format(Property.of(TEST_FLOW_ID)).build()))
            .build();

        flow = flowRepository.create(GenericFlow.of(flow));
        try {
            assertThat(flowRepository.findById(MAIN_TENANT, flow.getNamespace(), flow.getId()).isPresent()).isTrue();

            Flow update = Flow.builder()
                .id(flowId)
                .namespace(TEST_NAMESPACE)
                .tenantId(MAIN_TENANT)
                .tasks(Collections.singletonList(Return.builder().id(TEST_FLOW_ID).type(Return.class.getName()).format(Property.of(TEST_FLOW_ID)).build()))
                .build();
            ;

            Flow updated = flowRepository.update(GenericFlow.of(update), flow);
            assertThat(updated.getTriggers()).isNull();
        } finally {
            deleteFlow(flow);
        }

        Await.until(() -> FlowListener.getEmits().size() == 3, Duration.ofMillis(100), Duration.ofSeconds(5));
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.CREATE).count()).isEqualTo(1L);
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.UPDATE).count()).isEqualTo(1L);
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.DELETE).count()).isEqualTo(1L);
    }


    @Test
    void removeTriggerDelete() throws TimeoutException {
        String flowId = IdUtils.create();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace(TEST_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .triggers(Collections.singletonList(AbstractSchedulerTest.UnitTest.builder()
                .id("sleep")
                .type(AbstractSchedulerTest.UnitTest.class.getName())
                .build()))
            .tasks(Collections.singletonList(Return.builder().id(TEST_FLOW_ID).type(Return.class.getName()).format(Property.of(TEST_FLOW_ID)).build()))
            .build();

        Flow save = flowRepository.create(GenericFlow.of(flow));
        try {
            assertThat(flowRepository.findById(MAIN_TENANT, flow.getNamespace(), flow.getId()).isPresent()).isTrue();
        } finally {
            deleteFlow(save);
        }

        Await.until(() -> FlowListener.getEmits().size() == 2, Duration.ofMillis(100), Duration.ofSeconds(5));
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.CREATE).count()).isEqualTo(1L);
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.DELETE).count()).isEqualTo(1L);
    }

    @Test
    void findDistinctNamespace() {
        List<String> distinctNamespace = flowRepository.findDistinctNamespace(MAIN_TENANT);
        assertThat((long) distinctNamespace.size()).isEqualTo(8L);
    }

    @Test
    protected void shouldReturnNullRevisionForNonExistingFlow() {
        assertThat(flowRepository.lastRevision(TEST_TENANT_ID, TEST_NAMESPACE, IdUtils.create())).isNull();
    }

    @Test
    protected void shouldReturnLastRevisionOnCreate() {
        // Given
        final List<Flow> toDelete = new ArrayList<>();
        final String flowId = IdUtils.create();
        try {
            // When
            toDelete.add(flowRepository.create(createTestingLogFlow(flowId, "???")));
            Integer result = flowRepository.lastRevision(TEST_TENANT_ID, TEST_NAMESPACE, flowId);

            // Then
            assertThat(result).isEqualTo(1);
            assertThat(flowRepository.lastRevision(TEST_TENANT_ID, TEST_NAMESPACE, flowId)).isEqualTo(1);
        } finally {
            toDelete.forEach(this::deleteFlow);
        }
    }

    @Test
    protected void shouldIncrementRevisionOnDelete() {
        // Given
        final String flowId = IdUtils.create();
        FlowWithSource created = flowRepository.create(createTestingLogFlow(flowId, "first"));
        assertThat(flowRepository.findRevisions(TEST_TENANT_ID, TEST_NAMESPACE, flowId).size()).isEqualTo(1);

        // When
        flowRepository.delete(created);

        // Then
        assertThat(flowRepository.findRevisions(TEST_TENANT_ID, TEST_NAMESPACE, flowId).size()).isEqualTo(2);
    }

    @Test
    protected void shouldIncrementRevisionOnCreateAfterDelete() {
        // Given
        final List<Flow> toDelete = new ArrayList<>();
        final String flowId = IdUtils.create();
        try {
            // Given
            flowRepository.delete(
                flowRepository.create(createTestingLogFlow(flowId, "first"))
            );

            // When
            toDelete.add(flowRepository.create(createTestingLogFlow(flowId, "second")));

            // Then
            assertThat(flowRepository.findRevisions(TEST_TENANT_ID, TEST_NAMESPACE, flowId).size()).isEqualTo(3);
            assertThat(flowRepository.lastRevision(TEST_TENANT_ID, TEST_NAMESPACE, flowId)).isEqualTo(3);
        } finally {
            toDelete.forEach(this::deleteFlow);
        }
    }

    @Test
    protected void shouldReturnNullForLastRevisionAfterDelete() {
        // Given
        final List<Flow> toDelete = new ArrayList<>();
        final String flowId = IdUtils.create();
        try {
            // Given
            FlowWithSource created = flowRepository.create(createTestingLogFlow(flowId, "first"));
            toDelete.add(created);

            FlowWithSource updated = flowRepository.update(createTestingLogFlow(flowId, "second"), created);
            toDelete.add(updated);

            // When
            flowRepository.delete(updated);

            // Then
            assertThat(flowRepository.findById(TEST_TENANT_ID, TEST_NAMESPACE, flowId, Optional.empty())).isEqualTo(Optional.empty());
            assertThat(flowRepository.lastRevision(TEST_TENANT_ID, TEST_NAMESPACE, flowId)).isNull();
        } finally {
            toDelete.forEach(this::deleteFlow);
        }
    }

    @Test
    protected void shouldFindAllRevisionsAfterDelete() {
        // Given
        final List<Flow> toDelete = new ArrayList<>();
        final String flowId = IdUtils.create();
        try {
            // Given
            FlowWithSource created = flowRepository.create(createTestingLogFlow(flowId, "first"));
            toDelete.add(created);

            FlowWithSource updated = flowRepository.update(createTestingLogFlow(flowId, "second"), created);
            toDelete.add(updated);

            // When
            flowRepository.delete(updated);

            // Then
            assertThat(flowRepository.findById(TEST_TENANT_ID, TEST_NAMESPACE, flowId, Optional.empty())).isEqualTo(Optional.empty());
            assertThat(flowRepository.findRevisions(TEST_TENANT_ID, TEST_NAMESPACE, flowId).size()).isEqualTo(3);
        } finally {
            toDelete.forEach(this::deleteFlow);
        }
    }

    @Test
    protected void shouldIncrementRevisionOnUpdateGivenNotEqualSource() {
        final List<Flow> toDelete = new ArrayList<>();
        final String flowId = IdUtils.create();
        try {

            // Given
            FlowWithSource created = flowRepository.create(createTestingLogFlow(flowId, "first"));
            toDelete.add(created);

            // When
            FlowWithSource updated = flowRepository.update(createTestingLogFlow(flowId, "second"), created);
            toDelete.add(updated);

            // Then
            assertThat(updated.getRevision()).isEqualTo(2);
            assertThat(flowRepository.lastRevision(TEST_TENANT_ID, TEST_NAMESPACE, flowId)).isEqualTo(2);

        } finally {
            toDelete.forEach(this::deleteFlow);
        }
    }

    @Test
    protected void shouldNotIncrementRevisionOnUpdateGivenEqualSource() {
        final List<Flow> toDelete = new ArrayList<>();
        final String flowId = IdUtils.create();
        try {

            // Given
            FlowWithSource created = flowRepository.create(createTestingLogFlow(flowId, "first"));
            toDelete.add(created);

            // When
            FlowWithSource updated = flowRepository.update(createTestingLogFlow(flowId, "first"), created);
            toDelete.add(updated);

            // Then
            assertThat(updated.getRevision()).isEqualTo(1);
            assertThat(flowRepository.lastRevision(TEST_TENANT_ID, TEST_NAMESPACE, flowId)).isEqualTo(1);

        } finally {
            toDelete.forEach(this::deleteFlow);
        }
    }

    @Test
    void shouldReturnForFindGivenQueryWildcard() {
        ArrayListTotal<Flow> flows = flowRepository.find(Pageable.from(1, 10), "*", MAIN_TENANT, null, null, Map.of());
        assertThat(flows.size()).isEqualTo(10);
        assertThat(flows.getTotal()).isEqualTo(Helpers.FLOWS_COUNT);
    }

    @Test
    void shouldReturnForGivenQueryWildCardFilters() {
        List<QueryFilter> filters = List.of(
           QueryFilter.builder().field(QueryFilter.Field.QUERY).operation(QueryFilter.Op.EQUALS).value("*").build()
        );
        ArrayListTotal<Flow> flows = flowRepository.find(Pageable.from(1, 10), MAIN_TENANT, filters);
        assertThat(flows.size()).isEqualTo(10);
        assertThat(flows.getTotal()).isEqualTo(Helpers.FLOWS_COUNT);
    }

    @Test
    void findByExecution() {
        Flow flow = builder()
            .tenantId(MAIN_TENANT)
            .revision(1)
            .build();
        flowRepository.create(GenericFlow.of(flow));
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .tenantId(MAIN_TENANT)
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
        Flow flow = builder()
            .revision(3)
            .build();
        flowRepository.create(GenericFlow.of(flow));
        Execution execution = Execution.builder()
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
        FlowWithSource toDelete = null;
        try {
            // Given
            Flow flow = createTestFlowForNamespace(TEST_NAMESPACE);
            toDelete = flowRepository.create(GenericFlow.of(flow));
            // When
            int count = flowRepository.count(MAIN_TENANT);

            // Then
            Assertions.assertTrue(count > 0);
        } finally {
            Optional.ofNullable(toDelete).ifPresent(flow -> {
                flowRepository.delete(flow);
            });
        }
    }

    @Test
    void shouldCountForNullTenantGivenNamespace() {
        List<FlowWithSource> toDelete = new ArrayList<>();
        try {
            toDelete.add(flowRepository.create(GenericFlow.of(createTestFlowForNamespace("io.kestra.unittest.sub"))));
            toDelete.add(flowRepository.create(GenericFlow.of(createTestFlowForNamespace("io.kestra.unittest.shouldcountbynamespacefornulltenant"))));
            toDelete.add(flowRepository.create(GenericFlow.of(createTestFlowForNamespace("com.kestra.unittest"))));

            int count = flowRepository.countForNamespace(MAIN_TENANT, "io.kestra.unittest.shouldcountbynamespacefornulltenant");
            assertThat(count).isEqualTo(1);

            count = flowRepository.countForNamespace(MAIN_TENANT, TEST_NAMESPACE);
            assertThat(count).isEqualTo(2);
        } finally {
            for (FlowWithSource flow : toDelete) {
                flowRepository.delete(flow);
            }
        }
    }

    private static Flow createTestFlowForNamespace(String namespace) {
        return Flow.builder()
            .id(IdUtils.create())
            .namespace(namespace)
            .tenantId(MAIN_TENANT)
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
    public static class FlowListener implements ApplicationEventListener<CrudEvent<Flow>> {
        @Getter
        private static List<CrudEvent<Flow>> emits = new ArrayList<>();

        @Override
        public void onApplicationEvent(CrudEvent<Flow> event) {
            emits.add(event);
        }

        public static void reset() {
            emits = new ArrayList<>();
        }
    }

    private static GenericFlow createTestingLogFlow(String id, String logMessage) {
        String source = """
               id: %s
               namespace: %s
               tasks:
                 - id: log
                   type: io.kestra.plugin.core.log.Log
                   message: %s
            """.formatted(id, TEST_NAMESPACE, logMessage);
        return GenericFlow.fromYaml(TEST_TENANT_ID, source);
    }

}
