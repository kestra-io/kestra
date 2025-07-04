package io.kestra.core.repositories;

import io.kestra.core.Helpers;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionRepositoryInterface.ChildFilter;
import io.kestra.core.schedulers.AbstractSchedulerTest;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.debug.Return;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import lombok.Getter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.event.Level;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static io.kestra.core.models.flows.FlowScope.SYSTEM;
import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.kestra.core.utils.NamespaceUtils.SYSTEM_FLOWS_DEFAULT_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
            .tasks(Collections.singletonList(Return.builder().id(taskId).type(Return.class.getName()).format(Property.ofValue(TEST_FLOW_ID)).build()));
    }

    @ParameterizedTest
    @MethodSource("filterCombinations")
    void should_find_all(QueryFilter filter){

        FlowWithSource flow = FlowWithSource.builder()
            .id("filterFlowId")
            .namespace(SYSTEM_FLOWS_DEFAULT_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .labels(Label.from(Map.of("key", "value")))
            .build();
        flow = flowRepository.create(GenericFlow.of(flow));
        try {
            ArrayListTotal<Flow> entries = flowRepository.find(Pageable.UNPAGED, MAIN_TENANT, List.of(filter));

            assertThat(entries).hasSize(1);
        } finally {
            deleteFlow(flow);
        }
    }

    @ParameterizedTest
    @MethodSource("filterCombinations")
    void should_find_all_with_source(QueryFilter filter){

        FlowWithSource flow = FlowWithSource.builder()
            .id("filterFlowId")
            .namespace(SYSTEM_FLOWS_DEFAULT_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .labels(Label.from(Map.of("key", "value")))
            .build();
        flow = flowRepository.create(GenericFlow.of(flow));
        try {
            ArrayListTotal<FlowWithSource> entries = flowRepository.findWithSource(Pageable.UNPAGED, MAIN_TENANT, List.of(filter));

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
            QueryFilter.builder().field(Field.LABELS).value(Map.of("key", "value")).operation(Op.EQUALS).build()
        );
    }

    @ParameterizedTest
    @MethodSource("errorFilterCombinations")
    void should_fail_to_find_all(QueryFilter filter){
        assertThrows(
            InvalidQueryFiltersException.class,
            () -> flowRepository.find(Pageable.UNPAGED, MAIN_TENANT, List.of(filter)));

    }

    @ParameterizedTest
    @MethodSource("errorFilterCombinations")
    void should_fail_to_find_all_with_source(QueryFilter filter){
        assertThrows(
            InvalidQueryFiltersException.class,
            () -> flowRepository.findWithSource(Pageable.UNPAGED, MAIN_TENANT, List.of(filter)));

    }

    static Stream<QueryFilter> errorFilterCombinations() {
        return Stream.of(
            QueryFilter.builder().field(Field.FLOW_ID).value("sleep").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.START_DATE).value(ZonedDateTime.now().minusMinutes(1)).operation(Op.GREATER_THAN).build(),
            QueryFilter.builder().field(Field.END_DATE).value(ZonedDateTime.now().plusMinutes(1)).operation(Op.LESS_THAN).build(),
            QueryFilter.builder().field(Field.STATE).value(State.Type.RUNNING).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.TIME_RANGE).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).value("executionTriggerId").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.TRIGGER_ID).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.CHILD_FILTER).value(ChildFilter.CHILD).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.WORKER_ID).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.EXISTING_ONLY).value("test").operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.MIN_LEVEL).value(Level.DEBUG).operation(Op.EQUALS).build()
        );
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
    void findByNamespacePrefixWithSource() {
        List<FlowWithSource> save = flowRepository.findByNamespacePrefixWithSource(MAIN_TENANT, "io.kestra.tests");
        assertThat((long) save.size()).isEqualTo(Helpers.FLOWS_COUNT - 1);
    }

    @Test
    void find_paginationPartial() {
        assertThat(flowRepository.find(Pageable.from(1, (int) Helpers.FLOWS_COUNT - 1, Sort.UNSORTED), MAIN_TENANT, null)
            .size())
            .describedAs("When paginating at MAX-1, it should return MAX-1")
            .isEqualTo(Helpers.FLOWS_COUNT - 1);

        assertThat(flowRepository.findWithSource(Pageable.from(1, (int) Helpers.FLOWS_COUNT - 1, Sort.UNSORTED), MAIN_TENANT, null)
            .size())
            .describedAs("When paginating at MAX-1, it should return MAX-1")
            .isEqualTo(Helpers.FLOWS_COUNT - 1);
    }

    @Test
    void find_paginationGreaterThanExisting() {
        assertThat(flowRepository.find(Pageable.from(1, (int) Helpers.FLOWS_COUNT + 1, Sort.UNSORTED), MAIN_TENANT, null)
            .size())
            .describedAs("When paginating requesting a larger amount than existing, it should return existing MAX")
            .isEqualTo(Helpers.FLOWS_COUNT);
        assertThat(flowRepository.findWithSource(Pageable.from(1, (int) Helpers.FLOWS_COUNT + 1, Sort.UNSORTED), MAIN_TENANT, null)
            .size())
            .describedAs("When paginating requesting a larger amount than existing, it should return existing MAX")
            .isEqualTo(Helpers.FLOWS_COUNT);
    }

    @Test
    void find_prefixMatchingAllNamespaces() {
        assertThat(flowRepository.find(
            Pageable.UNPAGED,
            MAIN_TENANT,
            List.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.STARTS_WITH).value("io.kestra.tests").build()
            )
        ).size())
            .describedAs("When filtering on NAMESPACE START_WITH a pattern that match all, it should return all")
            .isEqualTo(Helpers.FLOWS_COUNT);

        assertThat(flowRepository.findWithSource(
            Pageable.UNPAGED,
            MAIN_TENANT,
            List.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.STARTS_WITH).value("io.kestra.tests").build()
            )
        ).size())
            .describedAs("When filtering on NAMESPACE START_WITH a pattern that match all, it should return all")
            .isEqualTo(Helpers.FLOWS_COUNT);
    }

    @Test
    void find_aSpecifiedNamespace() {
        assertThat(flowRepository.find(
            Pageable.UNPAGED,
            MAIN_TENANT,
            List.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value("io.kestra.tests2").build()
            )
        ).size()).isEqualTo(1L);

        assertThat(flowRepository.findWithSource(
            Pageable.UNPAGED,
            MAIN_TENANT,
            List.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value("io.kestra.tests2").build()
            )
        ).size()).isEqualTo(1L);
    }

    @Test
    void find_aSpecificSubNamespace() {
        assertThat(flowRepository.find(
            Pageable.UNPAGED,
            MAIN_TENANT,
            List.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value("io.kestra.tests.minimal.bis").build()
            )
        ).size())
            .isEqualTo(1L);

        assertThat(flowRepository.findWithSource(
            Pageable.UNPAGED,
            MAIN_TENANT,
            List.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value("io.kestra.tests.minimal.bis").build()
            )
        ).size())
            .isEqualTo(1L);
    }

    @Test
    void find_aSpecificLabel() {
        assertThat(
            flowRepository.find(Pageable.UNPAGED, MAIN_TENANT,
                List.of(
                    QueryFilter.builder().field(QueryFilter.Field.LABELS).operation(QueryFilter.Op.EQUALS).value(Map.of("country", "FR")).build()
                )
            ).size())
            .isEqualTo(1);

        assertThat(
            flowRepository.findWithSource(Pageable.UNPAGED, MAIN_TENANT,
                List.of(
                    QueryFilter.builder().field(QueryFilter.Field.LABELS).operation(QueryFilter.Op.EQUALS).value(Map.of("country", "FR")).build()
                )
            ).size())
            .isEqualTo(1);
    }

    @Test
    void find_aSpecificFlowByNamespaceAndLabel() {
        assertThat(
            flowRepository.find(Pageable.UNPAGED, MAIN_TENANT,
                List.of(
                    QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value("io.kestra.tests").build(),
                    QueryFilter.builder().field(QueryFilter.Field.LABELS).operation(QueryFilter.Op.EQUALS).value(Map.of("key2", "value2")).build()
                )
            ).size())
            .isEqualTo(1);

        assertThat(
            flowRepository.findWithSource(Pageable.UNPAGED, MAIN_TENANT,
                List.of(
                    QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value("io.kestra.tests").build(),
                    QueryFilter.builder().field(QueryFilter.Field.LABELS).operation(QueryFilter.Op.EQUALS).value(Map.of("key2", "value2")).build()
                )
            ).size())
            .isEqualTo(1);
    }

    @Test
    void find_noResult_forAnUnknownNamespace() {
        assertThat(
            flowRepository.find(Pageable.UNPAGED, MAIN_TENANT,
                List.of(
                    QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value("io.kestra.tests").build(),
                    QueryFilter.builder().field(QueryFilter.Field.LABELS).operation(QueryFilter.Op.EQUALS).value(Map.of("key1", "value2")).build()
                )
            ).size())
            .isEqualTo(0);

        assertThat(
            flowRepository.findWithSource(Pageable.UNPAGED, MAIN_TENANT,
                List.of(
                    QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value("io.kestra.tests").build(),
                    QueryFilter.builder().field(QueryFilter.Field.LABELS).operation(QueryFilter.Op.EQUALS).value(Map.of("key1", "value2")).build()
                )
            ).size())
            .isEqualTo(0);
    }

    @Test
    protected void findSpecialChars() {
        ArrayListTotal<SearchResult<Flow>> save = flowRepository.findSourceCode(Pageable.unpaged(), "https://api.chucknorris.io", MAIN_TENANT, null);
        assertThat((long) save.size()).isEqualTo(2L);
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
            .tasks(Collections.singletonList(Return.builder().id(TEST_FLOW_ID).type(Return.class.getName()).format(Property.ofValue(TEST_FLOW_ID)).build()))
            .build();

        Flow save = flowRepository.create(GenericFlow.of(flow));

        try {
            assertThat(flowRepository.findById(MAIN_TENANT, flow.getNamespace(), flow.getId()).isPresent()).isTrue();

            Flow update = Flow.builder()
                .id(IdUtils.create())
                .namespace("io.kestra.unittest2")
                .tenantId(MAIN_TENANT)
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
            .tasks(Collections.singletonList(Return.builder().id(TEST_FLOW_ID).type(Return.class.getName()).format(Property.ofValue(TEST_FLOW_ID)).build()))
            .build();

        flow = flowRepository.create(GenericFlow.of(flow));
        try {
            assertThat(flowRepository.findById(MAIN_TENANT, flow.getNamespace(), flow.getId()).isPresent()).isTrue();

            Flow update = Flow.builder()
                .id(flowId)
                .namespace(TEST_NAMESPACE)
                .tenantId(MAIN_TENANT)
                .tasks(Collections.singletonList(Return.builder().id(TEST_FLOW_ID).type(Return.class.getName()).format(Property.ofValue(TEST_FLOW_ID)).build()))
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
            .tasks(Collections.singletonList(Return.builder().id(TEST_FLOW_ID).type(Return.class.getName()).format(Property.ofValue(TEST_FLOW_ID)).build()))
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
