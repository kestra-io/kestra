package io.kestra.core.repositories;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import io.kestra.core.models.SearchResult;
import io.micronaut.data.model.Sort;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.event.Level;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.contexts.configuration.SystemFlowsConfiguration;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.Label;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.dashboards.AggregationType;
import io.kestra.core.models.dashboards.ColumnDescriptor;
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
import io.kestra.plugin.core.dashboard.data.Flows;
import io.kestra.plugin.core.dashboard.data.FlowsKPI;
import io.kestra.plugin.core.debug.Return;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static io.kestra.core.models.flows.FlowScope.SYSTEM;
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

    @Test
    void givenFlowWithTrigger_whenFindingFlowWithGivenTriggerClass_thenFindFlowWithTriggerClass() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        UnitTest trigger = UnitTest.builder()
            .id("trigger")
            .type(UnitTest.class.getName())
            .build();

        FlowWithSource flowWithTrigger = builder(tenant, "flow-with-trigger", TEST_FLOW_ID)
            .triggers(List.of(trigger))
            .build();
        FlowWithSource flowWithoutTrigger = builder(tenant, "flow-without-trigger", TEST_FLOW_ID)
            .build();

        flowWithTrigger = flowRepository.create(GenericFlow.of(flowWithTrigger));
        flowWithoutTrigger = flowRepository.create(GenericFlow.of(flowWithoutTrigger));

        try {
            // When
            ArrayListTotal<Flow> results = flowRepository.find(
                Pageable.UNPAGED,
                tenant,
                TEST_NAMESPACE,
                UnitTest.class
            );

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getId()).isEqualTo("flow-with-trigger");
        } finally {
            deleteFlow(flowWithTrigger);
            deleteFlow(flowWithoutTrigger);
        }
    }

    @Test
    void givenMultipleFlowWithTriggerIsDistinctNamespaceWithCommonPrefix_whenFindingFlowWithGivenTriggerClass_shouldFindFlowWithTriggerClassAndFullyMatchingNamespace() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String childNamespace = TEST_NAMESPACE + ".child";

        UnitTest trigger = UnitTest.builder()
            .id("trigger")
            .type(UnitTest.class.getName())
            .build();

        FlowWithSource flowInBaseNamespace = builder(tenant, "flow-in-base-namespace", TEST_FLOW_ID)
            .triggers(List.of(trigger))
            .build();
        FlowWithSource flowInChildNamespace = builder(tenant, "flow-in-child-namespace", TEST_FLOW_ID)
            .namespace(childNamespace)
            .triggers(List.of(trigger))
            .build();

        flowInBaseNamespace = flowRepository.create(GenericFlow.of(flowInBaseNamespace));
        flowInChildNamespace = flowRepository.create(GenericFlow.of(flowInChildNamespace));

        try {
            // When
            ArrayListTotal<Flow> results = flowRepository.find(
                Pageable.UNPAGED,
                tenant,
                TEST_NAMESPACE,
                UnitTest.class
            );

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getId()).isEqualTo("flow-in-base-namespace");
            assertThat(results.getFirst().getNamespace()).isEqualTo(TEST_NAMESPACE);
        } finally {
            deleteFlow(flowInBaseNamespace);
            deleteFlow(flowInChildNamespace);
        }
    }

    @ParameterizedTest
    @MethodSource("filterCombinations")
    void should_find_all(QueryFilter filter) {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow = FlowWithSource.builder()
            .id("filterFlowId")
            .namespace(SystemFlowsConfiguration.DEFAULT_NAMESPACE)
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
    void should_find_all_with_source(QueryFilter filter) {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow = FlowWithSource.builder()
            .id("filterFlowId")
            .namespace(SystemFlowsConfiguration.DEFAULT_NAMESPACE)
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
            QueryFilter.builder().field(Field.NAMESPACE).value(SystemFlowsConfiguration.DEFAULT_NAMESPACE).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.LABELS).value(Map.of("key", "value")).operation(Op.EQUALS).build(),
            QueryFilter.builder().field(Field.FLOW_ID).value("filterFlowId").operation(Op.EQUALS).build()
        );
    }

    @ParameterizedTest
    @MethodSource("errorFilterCombinations")
    void should_fail_to_find_all(QueryFilter filter) {
        assertThrows(
            InvalidQueryFiltersException.class,
            () -> flowRepository.find(Pageable.UNPAGED, TestsUtils.randomTenant(this.getClass().getSimpleName()), List.of(filter))
        );

    }

    @ParameterizedTest
    @MethodSource("errorFilterCombinations")
    void should_fail_to_find_all_with_source(QueryFilter filter) {
        assertThrows(
            InvalidQueryFiltersException.class,
            () -> flowRepository.findWithSource(Pageable.UNPAGED, TestsUtils.randomTenant(this.getClass().getSimpleName()), List.of(filter))
        );

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
            QueryFilter.builder().field(Field.LEVEL).value(Level.DEBUG).operation(Op.GREATER_THAN_OR_EQUAL_TO).build()
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
    void shouldFilterFlowsWithNotEqualsLabelOperator() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        FlowWithSource flowWithLabel = builder(tenant)
            .id("flow-with-label")
            .labels(Label.from(Map.of("foo", "bar")))
            .build();

        FlowWithSource flowWithoutLabel = builder(tenant)
            .id("flow-without-label")
            .build();

        FlowWithSource flowWithDifferentLabel = builder(tenant)
            .id("flow-with-different-label")
            .labels(Label.from(Map.of("foo", "baz")))
            .build();

        try {
            flowWithLabel = flowRepository.create(GenericFlow.of(flowWithLabel));
            flowWithoutLabel = flowRepository.create(GenericFlow.of(flowWithoutLabel));
            flowWithDifferentLabel = flowRepository.create(GenericFlow.of(flowWithDifferentLabel));

            // Filter: Labels NOT_EQUALS foo:bar
            // Should return: flow-without-label and flow-with-different-label
            QueryFilter filter = QueryFilter.builder()
                .field(QueryFilter.Field.LABELS)
                .operation(QueryFilter.Op.NOT_EQUALS)
                .value(Map.of("foo", "bar"))
                .build();

            ArrayListTotal<Flow> results = flowRepository.find(Pageable.UNPAGED, tenant, List.of(filter));

            assertThat(results).hasSize(2);
            assertThat(results)
                .extracting(Flow::getId)
                .containsExactlyInAnyOrder("flow-without-label", "flow-with-different-label");

        } finally {
            deleteFlow(flowWithLabel);
            deleteFlow(flowWithoutLabel);
            deleteFlow(flowWithDifferentLabel);
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

            full.ifPresent(current ->
            {
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
    void shouldRoundTripDraftField() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String flowId = IdUtils.create();
        FlowWithSource saved = flowRepository.create(createTestingLogFlow(tenant, flowId, "draft-flow", true));

        try {
            assertThat(saved.isDraft()).isTrue();
            Optional<Flow> reloaded = flowRepository.findById(tenant, TEST_NAMESPACE, flowId);
            assertThat(reloaded).isPresent();
            assertThat(reloaded.get().isDraft()).isTrue();
        } finally {
            deleteFlow(saved);
        }
    }

    @Test
    void shouldDefaultDraftToFalseWhenAbsent() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow = builder(tenant).build();
        FlowWithSource saved = flowRepository.create(GenericFlow.of(flow));

        try {
            assertThat(saved.isDraft()).isFalse();
        } finally {
            deleteFlow(saved);
        }
    }

    @Test
    void findByIdForExecution_shouldReturnLatestRevisionWhenNoDraftExists() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String flowId = IdUtils.create();
        final List<Flow> toDelete = new ArrayList<>();

        try {
            FlowWithSource r1 = flowRepository.create(createTestingLogFlow(tenant, flowId, "first"));
            toDelete.add(r1);
            FlowWithSource r2 = flowRepository.update(createTestingLogFlow(tenant, flowId, "second"), r1);
            toDelete.add(r2);

            Optional<Flow> found = flowRepository.findByIdForExecution(tenant, TEST_NAMESPACE, flowId);
            assertThat(found).isPresent();
            assertThat(found.get().getRevision()).isEqualTo(r2.getRevision());
            assertThat(found.get().isDraft()).isFalse();
        } finally {
            toDelete.forEach(this::deleteFlow);
        }
    }

    @Test
    void findByIdForExecution_shouldFallBackToLastNonDraftWhenLatestIsDraft() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String flowId = IdUtils.create();
        final List<Flow> toDelete = new ArrayList<>();

        try {
            FlowWithSource published = flowRepository.create(createTestingLogFlow(tenant, flowId, "published", false));
            toDelete.add(published);
            FlowWithSource draft = flowRepository.update(createTestingLogFlow(tenant, flowId, "wip", true), published);
            toDelete.add(draft);

            // findById (the view-time lookup) returns the draft, since it is the latest revision...
            Optional<Flow> latest = flowRepository.findById(tenant, TEST_NAMESPACE, flowId);
            assertThat(latest).isPresent();
            assertThat(latest.get().getRevision()).isEqualTo(draft.getRevision());
            assertThat(latest.get().isDraft()).isTrue();

            // ...while the execution-time lookup falls back to the most recent non-draft.
            Optional<Flow> executable = flowRepository.findByIdForExecution(tenant, TEST_NAMESPACE, flowId);
            assertThat(executable).isPresent();
            assertThat(executable.get().getRevision()).isEqualTo(published.getRevision());
            assertThat(executable.get().isDraft()).isFalse();

            Optional<FlowWithSource> executableWithSource = flowRepository.findByIdWithSourceForExecution(tenant, TEST_NAMESPACE, flowId);
            assertThat(executableWithSource).isPresent();
            assertThat(executableWithSource.get().getRevision()).isEqualTo(published.getRevision());
        } finally {
            toDelete.forEach(this::deleteFlow);
        }
    }

    @Test
    void findByIdForExecution_shouldReturnEmptyWhenAllRevisionsAreDraft() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String flowId = IdUtils.create();
        FlowWithSource draftOnly = flowRepository.create(createTestingLogFlow(tenant, flowId, "wip", true));

        try {
            assertThat(flowRepository.findByIdForExecution(tenant, TEST_NAMESPACE, flowId)).isEmpty();
            assertThat(flowRepository.findByIdWithSourceForExecution(tenant, TEST_NAMESPACE, flowId)).isEmpty();
            // The draft revision is still reachable when explicitly requested.
            assertThat(flowRepository.findById(tenant, TEST_NAMESPACE, flowId, Optional.of(draftOnly.getRevision()))).isPresent();
        } finally {
            deleteFlow(draftOnly);
        }
    }

    @Test
    void findAllWithSourceForExecutionForAllTenants_shouldExcludeFlowsWhoseLatestIsDraft() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String publishedId = IdUtils.create();
        String draftLatestId = IdUtils.create();
        String draftOnlyId = IdUtils.create();
        final List<Flow> toDelete = new ArrayList<>();

        try {
            FlowWithSource published = flowRepository.create(createTestingLogFlow(tenant, publishedId, "p1"));
            toDelete.add(published);

            FlowWithSource draftLatestR1 = flowRepository.create(createTestingLogFlow(tenant, draftLatestId, "head"));
            toDelete.add(draftLatestR1);
            FlowWithSource draftLatestR2 = flowRepository.update(createTestingLogFlow(tenant, draftLatestId, "wip", true), draftLatestR1);
            toDelete.add(draftLatestR2);

            FlowWithSource draftOnly = flowRepository.create(createTestingLogFlow(tenant, draftOnlyId, "wip", true));
            toDelete.add(draftOnly);

            List<FlowWithSource> executable = flowRepository.findAllWithSourceForExecutionForAllTenants();
            List<String> executableIds = executable.stream().map(Flow::getId).toList();

            // The fully-published flow appears at its only revision.
            assertThat(executable.stream().filter(f -> f.getId().equals(publishedId)).findFirst())
                .isPresent()
                .hasValueSatisfying(f -> assertThat(f.getRevision()).isEqualTo(published.getRevision()));

            // The flow whose latest is a draft falls back to the previous non-draft revision.
            assertThat(executable.stream().filter(f -> f.getId().equals(draftLatestId)).findFirst())
                .isPresent()
                .hasValueSatisfying(f ->
                {
                    assertThat(f.getRevision()).isEqualTo(draftLatestR1.getRevision());
                    assertThat(f.isDraft()).isFalse();
                });

            // A flow with only draft revisions is not exposed at all.
            assertThat(executableIds).doesNotContain(draftOnlyId);
        } finally {
            toDelete.forEach(this::deleteFlow);
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
    void findByNamespaceExecutable_shouldReturnFlowWhenEnabled() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Flow flow = flowRepository.create(GenericFlow.of(builder(tenant).build()));

        try {
            List<FlowForExecution> executable = flowRepository.findByNamespaceExecutable(tenant, TEST_NAMESPACE);

            assertThat(executable).hasSize(1);
            assertThat(executable.getFirst().getId()).isEqualTo(flow.getId());
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    void findByNamespaceExecutable_shouldExcludeFlowWhenDisabled() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Flow flow = flowRepository.create(GenericFlow.of(builder(tenant).disabled(true).build()));

        try {
            assertThat(flow.isDisabled()).isTrue();
            assertThat(flowRepository.findByNamespaceExecutable(tenant, TEST_NAMESPACE)).isEmpty();
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    void findByNamespaceExecutable_shouldExcludeFlowWhenDeleted() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Flow flow = flowRepository.create(GenericFlow.of(builder(tenant).build()));

        deleteFlow(flow);

        assertThat(flowRepository.findByNamespaceExecutable(tenant, TEST_NAMESPACE)).isEmpty();
    }

    @Test
    void findDistinctNamespaceExecutable_shouldReturnNamespaceWhenEnabled() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Flow flow = flowRepository.create(GenericFlow.of(builder(tenant).build()));

        try {
            assertThat(flowRepository.findDistinctNamespaceExecutable(tenant)).containsExactly(TEST_NAMESPACE);
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    void findDistinctNamespaceExecutable_shouldExcludeNamespaceWhenOnlyFlowIsDisabled() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Flow flow = flowRepository.create(GenericFlow.of(builder(tenant).disabled(true).build()));

        try {
            assertThat(flow.isDisabled()).isTrue();
            assertThat(flowRepository.findDistinctNamespaceExecutable(tenant)).isEmpty();
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    void findDistinctNamespaceExecutable_shouldExcludeNamespaceWhenOnlyFlowIsDeleted() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Flow flow = flowRepository.create(GenericFlow.of(builder(tenant).build()));

        deleteFlow(flow);

        assertThat(flowRepository.findDistinctNamespaceExecutable(tenant)).isEmpty();
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

        List<FlowWithSource> revisions = flowRepository.findRevisions(tenant, flow.getNamespace(), flow.getId(), true);
        assertThat(revisions.getLast().getRevision()).isEqualTo(delete.getRevision());
    }

    @Test
    protected void shouldDeleteRevisions() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        final List<Flow> toDelete = new ArrayList<>();
        final String flowId = IdUtils.create();
        try {
            FlowWithSource revision1 = flowRepository.create(createTestingLogFlow(tenant, flowId, "first"));
            toDelete.add(revision1);

            FlowWithSource revision2 = flowRepository.update(createTestingLogFlow(tenant, flowId, "second"), revision1);
            toDelete.add(revision2);

            FlowWithSource revision3 = flowRepository.update(createTestingLogFlow(tenant, flowId, "third"), revision2);
            toDelete.add(revision3);

            flowRepository.deleteRevisions(tenant, TEST_NAMESPACE, flowId, List.of(1, 2));

            List<FlowWithSource> revisions = flowRepository.findRevisions(tenant, TEST_NAMESPACE, flowId, false);

            assertThat(revisions).hasSize(1);
            assertThat(revisions.getFirst()).usingRecursiveComparison().ignoringFields("triggers", "updated").isEqualTo(revision3);

        } finally {
            toDelete.forEach(this::deleteFlow);
        }
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
            .triggers(
                Collections.singletonList(
                    UnitTest.builder()
                        .id("sleep")
                        .type(UnitTest.class.getName())
                        .build()
                )
            )
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

        Await.until(
            () -> FlowListener.filterByTenant(tenant)
                .size() == 3,
            Duration.ofMillis(100), Duration.ofSeconds(5)
        );
        assertThat(
            FlowListener.filterByTenant(tenant).stream()
                .filter(r -> r.getType() == CrudEventType.CREATE).count()
        ).isEqualTo(1L);
        assertThat(
            FlowListener.filterByTenant(tenant).stream()
                .filter(r -> r.getType() == CrudEventType.UPDATE).count()
        ).isEqualTo(1L);
        assertThat(
            FlowListener.filterByTenant(tenant).stream()
                .filter(r -> r.getType() == CrudEventType.DELETE).count()
        ).isEqualTo(1L);
    }

    @Test
    void removeTriggerDelete() throws TimeoutException {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String flowId = IdUtils.create();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace(TEST_NAMESPACE)
            .tenantId(tenant)
            .triggers(
                Collections.singletonList(
                    UnitTest.builder()
                        .id("sleep")
                        .type(UnitTest.class.getName())
                        .build()
                )
            )
            .tasks(Collections.singletonList(Return.builder().id(TEST_FLOW_ID).type(Return.class.getName()).format(Property.ofValue(TEST_FLOW_ID)).build()))
            .build();

        Flow save = flowRepository.create(GenericFlow.of(flow));
        try {
            assertThat(flowRepository.findById(tenant, flow.getNamespace(), flow.getId()).isPresent()).isTrue();
        } finally {
            deleteFlow(save);
        }

        Await.until(
            () -> FlowListener.filterByTenant(tenant)
                .size() == 2,
            Duration.ofMillis(100), Duration.ofSeconds(5)
        );
        assertThat(
            FlowListener.filterByTenant(tenant).stream()
                .filter(r -> r.getType() == CrudEventType.CREATE).count()
        ).isEqualTo(1L);
        assertThat(
            FlowListener.filterByTenant(tenant).stream()
                .filter(r -> r.getType() == CrudEventType.DELETE).count()
        ).isEqualTo(1L);
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
        assertThat(flowRepository.findRevisions(tenant, TEST_NAMESPACE, flowId, true).size()).isEqualTo(1);

        // When
        flowRepository.delete(created);

        // Then
        assertThat(flowRepository.findRevisions(tenant, TEST_NAMESPACE, flowId, true).size()).isEqualTo(2);
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
            assertThat(flowRepository.findRevisions(tenant, TEST_NAMESPACE, flowId, true).size()).isEqualTo(3);
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
            assertThat(flowRepository.findRevisions(tenant, TEST_NAMESPACE, flowId, true).size()).isEqualTo(3);
            assertThat(flowRepository.findRevisions(tenant, TEST_NAMESPACE, flowId, false).size()).isEqualTo(2);
        } finally {
            toDelete.forEach(this::deleteFlow);
        }
    }

    @Test
    protected void shouldFindRevisions() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        final List<Flow> toDelete = new ArrayList<>();
        final String flowId = IdUtils.create();
        try {
            FlowWithSource revision1 = flowRepository.create(createTestingLogFlow(tenant, flowId, "first"));
            toDelete.add(revision1);

            FlowWithSource revision2 = flowRepository.update(createTestingLogFlow(tenant, flowId, "second"), revision1);
            toDelete.add(revision2);

            FlowWithSource revision3 = flowRepository.update(createTestingLogFlow(tenant, flowId, "third"), revision2);
            toDelete.add(revision3);

            FlowWithSource revision4 = flowRepository.update(createTestingLogFlow(tenant, flowId, "fourth"), revision3);
            toDelete.add(revision4);

            List<FlowWithSource> revisions = flowRepository.findRevisions(
                tenant, TEST_NAMESPACE,
                flowId, null, List.of(1, 3, 4)
            );

            assertThat(revisions).hasSize(3);
            assertThat(revisions.get(0)).usingRecursiveComparison().ignoringFields("triggers", "updated").isEqualTo(revision1);
            assertThat(revisions.get(1)).usingRecursiveComparison().ignoringFields("triggers", "updated").isEqualTo(revision3);
            assertThat(revisions.get(2)).usingRecursiveComparison().ignoringFields("triggers", "updated").isEqualTo(revision4);

        } finally {
            toDelete.forEach(this::deleteFlow);
        }
    }

    @Test
    protected void shouldReturnUpdatedInFindRevisions() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        final List<Flow> toDelete = new ArrayList<>();
        final String flowId = IdUtils.create();
        try {
            // When: Create a flow with multiple revisions
            FlowWithSource created = flowRepository.create(createTestingLogFlow(tenant, flowId, "first"));
            toDelete.add(created);

            FlowWithSource updated = flowRepository.update(createTestingLogFlow(tenant, flowId, "second"), created);
            toDelete.add(updated);

            // Then: findRevisions should return updated for each revision
            List<FlowWithSource> revisions = flowRepository.findRevisions(tenant, TEST_NAMESPACE, flowId, true);

            assertThat(revisions).hasSize(2);

            // Each revision should have an updated timestamp
            for (FlowWithSource revision : revisions) {
                assertThat(revision.getUpdated())
                    .as("Revision %d should have updated", revision.getRevision())
                    .isNotNull();
            }

            // Revisions should be ordered by revision number
            assertThat(revisions.get(0).getRevision()).isEqualTo(1);
            assertThat(revisions.get(1).getRevision()).isEqualTo(2);
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
    void shouldFilterSourceCodeByNamespaceAndQuery() {
        // Given — two flows in the same tenant but different namespaces.
        // Flow IDs use "alpha" / "beta" as distinct tokens that do not appear in each
        // other's YAML (the common task type io.kestra.plugin.core.debug.Return
        // contains neither word), enabling unambiguous query-filter assertions.
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String namespaceA = "io.kestra.findsource.a";
        String namespaceB = "io.kestra.findsource.b";

        FlowWithSource flowA = builder(tenant, "source-flow-alpha", TEST_FLOW_ID)
            .namespace(namespaceA)
            .build();
        FlowWithSource flowB = builder(tenant, "source-flow-beta", TEST_FLOW_ID)
            .namespace(namespaceB)
            .build();

        flowA = flowRepository.create(GenericFlow.of(flowA));
        flowB = flowRepository.create(GenericFlow.of(flowB));

        try {
            // When — filter by namespace only
            ArrayListTotal<SearchResult<Flow>> byNamespaceA = flowRepository.findSourceCode(
                Pageable.UNPAGED, null, tenant, namespaceA
            );

            // Then — only the flow in namespaceA is returned
            assertThat(byNamespaceA)
                .as(
                    "Expected only namespace %s but got: %s", namespaceA,
                    byNamespaceA.stream().map(r -> r.getModel().getNamespace()).toList()
                )
                .hasSize(1);
            assertThat(byNamespaceA.getFirst().getModel().getNamespace()).isEqualTo(namespaceA);

            // When — filter by query using a token unique to flow-beta's source
            ArrayListTotal<SearchResult<Flow>> byQuery = flowRepository.findSourceCode(
                Pageable.UNPAGED, "beta", tenant, null
            );

            // Then — only the flow whose source contains "beta" is returned
            assertThat(byQuery)
                .as(
                    "Expected only flow-beta but got: %s",
                    byQuery.stream().map(r -> r.getModel().getId()).toList()
                )
                .hasSize(1);
            assertThat(byQuery.getFirst().getModel().getId()).isEqualTo("source-flow-beta");
        } finally {
            deleteFlow(flowA);
            deleteFlow(flowB);
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
            Optional.ofNullable(toDelete).ifPresent(flow ->
            {
                flowRepository.delete(flow);
            });
        }
    }

    @Test
    void should_exist_for_tenant() {
        String tenantFlowExist = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flowExist = FlowWithSource.builder()
            .id("flowExist")
            .namespace(SystemFlowsConfiguration.DEFAULT_NAMESPACE)
            .tenantId(tenantFlowExist)
            .deleted(false)
            .build();
        flowExist = flowRepository.create(GenericFlow.of(flowExist));

        String tenantFlowDeleted = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flowDeleted = FlowWithSource.builder()
            .id("flowDeleted")
            .namespace(SystemFlowsConfiguration.DEFAULT_NAMESPACE)
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

    @Test
    protected void dashboard_fetchData_shouldNotReturnDuplicateFlowRevisions() throws Exception {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        var flowId = IdUtils.create();

        // Create flow with revision 1
        FlowWithSource revision1 = flowRepository.create(createTestingLogFlow(tenant, flowId, "first"));
        // Update to create revision 2
        FlowWithSource revision2 = flowRepository.update(createTestingLogFlow(tenant, flowId, "second"), revision1);
        // Update to create revision 3
        FlowWithSource revision3 = flowRepository.update(createTestingLogFlow(tenant, flowId, "third"), revision2);

        try {
            var now = ZonedDateTime.now();
            ArrayListTotal<Map<String, Object>> data = flowRepository.fetchData(
                tenant,
                Flows.<ColumnDescriptor<Flows.Fields>> builder()
                    .type(Flows.class.getName())
                    .columns(
                        Map.of(
                            "id", ColumnDescriptor.<Flows.Fields> builder().field(Flows.Fields.ID).build(),
                            "namespace", ColumnDescriptor.<Flows.Fields> builder().field(Flows.Fields.NAMESPACE).build()
                        )
                    )
                    .build(),
                now.minusHours(1),
                now,
                null
            );

            // Should return only 1 row (latest revision), not 3
            assertThat(data.getTotal()).isEqualTo(1L);
            assertThat(data).hasSize(1);
        } finally {
            deleteFlow(revision3);
        }
    }

    @Test
    protected void dashboard_fetchValue_shouldNotCountDuplicateFlowRevisions() throws Exception {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        var flowId = IdUtils.create();

        // Create flow with revision 1
        FlowWithSource revision1 = flowRepository.create(createTestingLogFlow(tenant, flowId, "first"));
        // Update to create revision 2
        FlowWithSource revision2 = flowRepository.update(createTestingLogFlow(tenant, flowId, "second"), revision1);
        // Update to create revision 3
        FlowWithSource revision3 = flowRepository.update(createTestingLogFlow(tenant, flowId, "third"), revision2);

        try {
            var now = ZonedDateTime.now();
            Double value = flowRepository.fetchValue(
                tenant,
                FlowsKPI.<ColumnDescriptor<FlowsKPI.Fields>> builder()
                    .type(FlowsKPI.class.getName())
                    .columns(
                        ColumnDescriptor.<FlowsKPI.Fields> builder()
                            .field(FlowsKPI.Fields.ID)
                            .agg(AggregationType.COUNT)
                            .build()
                    )
                    .build(),
                now.minusHours(1),
                now,
                false
            );

            // Should count only 1 flow (latest revision), not 3
            assertEquals(1.0, value);
        } finally {
            deleteFlow(revision3);
        }
    }

    @Test
    protected void shouldFindWithCombinedNamespaceAndQueryFilter(){
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        FlowWithSource match = FlowWithSource.builder()
            .id("combined-match")
            .namespace(TEST_NAMESPACE)
            .tenantId(tenant)
            .build();

        FlowWithSource noMatch = FlowWithSource.builder()
            .id("combined-nomatch")
            .namespace("io.kestra.other")
            .tenantId(tenant)
            .build();

        match = flowRepository.create(GenericFlow.of(match));
        noMatch = flowRepository.create(GenericFlow.of(noMatch));

        try{
            List<QueryFilter> filters = List.of(
                QueryFilter.builder().field(Field.NAMESPACE).value(TEST_NAMESPACE).operation(Op.EQUALS).build(),
                QueryFilter.builder().field(Field.QUERY).value("combined-match").operation(Op.EQUALS).build()
            );

            ArrayListTotal<Flow> results = flowRepository.find(Pageable.UNPAGED, tenant, filters);
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getId()).isEqualTo("combined-match");
        }
        finally {
            deleteFlow(match);
            deleteFlow(noMatch);
        }
    }

    @Test
    protected void shouldFindWithNamespacePrefixFilter(){
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        FlowWithSource inPrefix = FlowWithSource.builder()
            .id("prefix-match")
            .namespace(TEST_NAMESPACE + ".child")
            .tenantId(tenant)
            .build();

        FlowWithSource outPrefix = FlowWithSource.builder()
            .id("prefix-nomatch")
            .namespace("io.kestra.other")
            .tenantId(tenant)
            .build();

        inPrefix = flowRepository.create(GenericFlow.of(inPrefix));
        outPrefix = flowRepository.create(GenericFlow.of(outPrefix));

        try {

            List<Flow> results = flowRepository.findByNamespacePrefix(tenant, TEST_NAMESPACE);
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getId()).isEqualTo("prefix-match");
        }
        finally {
            deleteFlow(inPrefix);
            deleteFlow(outPrefix);
        }
    }

    @Test
    protected void shouldFindByNamespacePrefixWithSource() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        FlowWithSource inPrefix = flowRepository.create(
            GenericFlow.fromYaml(tenant, """
            id: prefix-match
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(TEST_NAMESPACE + ".child"))
        );

        FlowWithSource outPrefix = flowRepository.create(
            GenericFlow.fromYaml(tenant, """
            id: prefix-nomatch
            namespace: io.kestra.other
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """)
        );

        try {
            List<FlowWithSource> results = flowRepository.findByNamespacePrefixWithSource(tenant, TEST_NAMESPACE);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getId()).isEqualTo("prefix-match");
            assertThat(results.getFirst().getSource()).isNotNull();
        } finally {
            deleteFlow(inPrefix);
            deleteFlow(outPrefix);
        }
    }

    @Test
    void shouldFindSourceCode() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        FlowWithSource flow = flowRepository.create(
            GenericFlow.fromYaml(tenant, """
            id: sourcecode-test
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: unique-searchable-string
            """.formatted(TEST_NAMESPACE))
        );

        try {
            ArrayListTotal<SearchResult<Flow>> results = flowRepository.findSourceCode(
                Pageable.UNPAGED, "unique-searchable-string", tenant, TEST_NAMESPACE
            );

            assertThat(results.getTotal()).isGreaterThanOrEqualTo(1);
            assertThat(results.stream()
                .map(r -> r.getModel().getId())
                .toList()
            ).contains("sourcecode-test");
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    protected void shouldFindDistinctNamespace() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        FlowWithSource flow1 = flowRepository.create(
            GenericFlow.fromYaml(tenant, """
            id: distinct-ns-flow1
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(TEST_NAMESPACE))
        );

        FlowWithSource flow2 = flowRepository.create(
            GenericFlow.fromYaml(tenant, """
            id: distinct-ns-flow2
            namespace: io.kestra.other
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """)
        );

        FlowWithSource flow3 = flowRepository.create(
            GenericFlow.fromYaml(tenant, """
            id: distinct-ns-flow3
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(TEST_NAMESPACE))
        );

        try {
            List<String> results = flowRepository.findDistinctNamespace(tenant);
            assertThat(results).contains(TEST_NAMESPACE, "io.kestra.other");
            assertThat(results).doesNotHaveDuplicates();
        } finally {
            deleteFlow(flow1);
            deleteFlow(flow2);
            deleteFlow(flow3);
        }
    }

    @Test
    protected void shouldFindByNamespace() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        FlowWithSource flow1 = flowRepository.create(
            GenericFlow.fromYaml(tenant, """
            id: find-by-namespace-match
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(TEST_NAMESPACE))
        );

        FlowWithSource flow2 = flowRepository.create(
            GenericFlow.fromYaml(tenant, """
            id: find-by-namespace-nomatch
            namespace: io.kestra.other
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """)
        );

        try {
            List<Flow> results = flowRepository.findByNamespace(tenant, TEST_NAMESPACE);
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getNamespace()).isEqualTo(TEST_NAMESPACE);
        } finally {
            deleteFlow(flow1);
            deleteFlow(flow2);
        }
    }

    @Test
    protected void shouldFindByNamespaceExecutable() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        FlowWithSource activeFlow = flowRepository.create(
            GenericFlow.fromYaml(tenant, """
            id: executable-active
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(TEST_NAMESPACE))
        );

        FlowWithSource disabledFlow = flowRepository.create(
            GenericFlow.fromYaml(tenant, """
            id: executable-disabled
            namespace: %s
            disabled: true
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(TEST_NAMESPACE))
        );

        FlowWithSource deletedFlow = flowRepository.create(
            GenericFlow.fromYaml(tenant, """
            id: executable-deleted
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(TEST_NAMESPACE))
        );
        flowRepository.delete(deletedFlow);

        try {
            List<FlowForExecution> results = flowRepository.findByNamespaceExecutable(tenant, TEST_NAMESPACE);

            // only active flow should be returned
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getId()).isEqualTo(activeFlow.getId());

            // disabled flow should not be executable
            assertThat(results.stream().map(FlowForExecution::getId).toList())
                .doesNotContain(disabledFlow.getId());

            // deleted flow should not be returned
            assertThat(results.stream().map(FlowForExecution::getId).toList())
                .doesNotContain(deletedFlow.getId());
        } finally {
            deleteFlow(activeFlow);
            deleteFlow(disabledFlow);
            deleteFlow(deletedFlow);
        }
    }

    @Test
    protected void shouldFindDistinctNamespaceExecutable() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        FlowWithSource activeFlow = flowRepository.create(
            GenericFlow.fromYaml(tenant, """
            id: distinct-executable-active
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(TEST_NAMESPACE))
        );

        FlowWithSource disabledFlow = flowRepository.create(
            GenericFlow.fromYaml(tenant, """
            id: distinct-executable-disabled
            namespace: io.kestra.disabled
            disabled: true
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """)
        );

        FlowWithSource deletedFlow = flowRepository.create(
            GenericFlow.fromYaml(tenant, """
            id: distinct-executable-deleted
            namespace: io.kestra.deleted
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """)
        );
        flowRepository.delete(deletedFlow);

        try {
            List<String> results = flowRepository.findDistinctNamespaceExecutable(tenant);

            // only namespace with active flow should appear
            assertThat(results).contains(TEST_NAMESPACE);

            // namespace with only disabled flow should not appear
            assertThat(results).doesNotContain("io.kestra.disabled");

            // namespace with only deleted flow should not appear
            assertThat(results).doesNotContain("io.kestra.deleted");
        } finally {
            deleteFlow(activeFlow);
            deleteFlow(disabledFlow);
            deleteFlow(deletedFlow);
        }
    }

    @Test
    protected void shouldFindWithNullFilters() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow = flowRepository.create(createTestingLogFlow(tenant, IdUtils.create(), "test"));

        try {
            // filters = null
            ArrayListTotal<Flow> results = flowRepository.find(Pageable.UNPAGED, tenant, (List<QueryFilter>) null);
            assertThat(results).hasSize(1);
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    protected void shouldFindWithPagination() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow1 = flowRepository.create(createTestingLogFlow(tenant, "aaa-flow", "first"));
        FlowWithSource flow2 = flowRepository.create(createTestingLogFlow(tenant, "bbb-flow", "second"));
        FlowWithSource flow3 = flowRepository.create(createTestingLogFlow(tenant, "ccc-flow", "third"));

        try {
            // page = 1, size = 2
            ArrayListTotal<Flow> page1 = flowRepository.find(Pageable.from(1, 2), tenant, (List<QueryFilter>) null);
            assertThat(page1.getTotal()).isEqualTo(3);
            assertThat(page1).hasSize(2);

            // page = 2, size = 2
            ArrayListTotal<Flow> page2 = flowRepository.find(Pageable.from(2, 2), tenant, (List<QueryFilter>) null);
            assertThat(page2.getTotal()).isEqualTo(3);
            assertThat(page2).hasSize(1);
        } finally {
            deleteFlow(flow1);
            deleteFlow(flow2);
            deleteFlow(flow3);
        }
    }

    @Test
    protected void shouldFindWithSort() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow1 = flowRepository.create(createTestingLogFlow(tenant, "aaa-flow", "first"));
        FlowWithSource flow2 = flowRepository.create(createTestingLogFlow(tenant, "bbb-flow", "second"));
        FlowWithSource flow3 = flowRepository.create(createTestingLogFlow(tenant, "ccc-flow", "third"));

        try {
            // sort by id ascending
            ArrayListTotal<Flow> asc = flowRepository.find(
                Pageable.from(1, 10, Sort.of(Sort.Order.asc("id"))), tenant, (List<QueryFilter>) null
            );
            assertThat(asc).extracting(Flow::getId)
                .containsExactly("aaa-flow", "bbb-flow", "ccc-flow");

            // sort by id descending
            ArrayListTotal<Flow> desc = flowRepository.find(
                Pageable.from(1, 10, Sort.of(Sort.Order.desc("id"))), tenant, (List<QueryFilter>) null
            );
            assertThat(desc).extracting(Flow::getId)
                .containsExactly("ccc-flow", "bbb-flow", "aaa-flow");
        } finally {
            deleteFlow(flow1);
            deleteFlow(flow2);
            deleteFlow(flow3);
        }
    }

    @Test
    protected void shouldFindWithNullTriggerClass() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow = flowRepository.create(createTestingLogFlow(tenant, IdUtils.create(), "test"));

        try {
            // triggerClass is set to null and should return all flows
            ArrayListTotal<Flow> results = flowRepository.find(Pageable.UNPAGED, tenant, (Class<? extends AbstractTrigger>) null);
            assertThat(results).isNotEmpty();
            assertThat(results.stream().map(Flow::getId).toList()).contains(flow.getId());
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    protected void shouldFindWithSourceNullFilters() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow = flowRepository.create(createTestingLogFlow(tenant, IdUtils.create(), "test"));

        try {
            // filters is set to null
            ArrayListTotal<FlowWithSource> results = flowRepository.findWithSource(Pageable.UNPAGED, tenant, null);
            assertThat(results).isNotEmpty();
            assertThat(results.stream().map(FlowWithSource::getId).toList()).contains(flow.getId());
            assertThat(results).allMatch(f -> f.getSource() != null);
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    protected void shouldFindWithSourcePagination() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow1 = flowRepository.create(createTestingLogFlow(tenant, "aaa-flow", "first"));
        FlowWithSource flow2 = flowRepository.create(createTestingLogFlow(tenant, "bbb-flow", "second"));
        FlowWithSource flow3 = flowRepository.create(createTestingLogFlow(tenant, "ccc-flow", "third"));

        try {
            ArrayListTotal<FlowWithSource> page1 = flowRepository.findWithSource(Pageable.from(1, 2), tenant, null);
            assertThat(page1.getTotal()).isEqualTo(3);
            assertThat(page1).hasSize(2);

            ArrayListTotal<FlowWithSource> page2 = flowRepository.findWithSource(Pageable.from(2, 2), tenant, null);
            assertThat(page2.getTotal()).isEqualTo(3);
            assertThat(page2).hasSize(1);
        } finally {
            deleteFlow(flow1);
            deleteFlow(flow2);
            deleteFlow(flow3);
        }
    }

    @Test
    protected void shouldFindSourceCodeWithNullQuery() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow = flowRepository.create(
            GenericFlow.fromYaml(tenant, """
            id: sourcecode-null-query
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(TEST_NAMESPACE))
        );

        try {
            ArrayListTotal<SearchResult<Flow>> results = flowRepository.findSourceCode(
                Pageable.UNPAGED, null, tenant, null
            );
            assertThat(results).isNotEmpty();
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    protected void shouldFindAllForAllTenants() {
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());

        FlowWithSource flow1 = flowRepository.create(createTestingLogFlow(tenant1, IdUtils.create(), "first"));
        FlowWithSource flow2 = flowRepository.create(createTestingLogFlow(tenant2, IdUtils.create(), "second"));

        try {
            List<Flow> results = flowRepository.findAllForAllTenants();
            assertThat(results.stream().map(Flow::getId).toList())
                .contains(flow1.getId(), flow2.getId());
        } finally {
            deleteFlow(flow1);
            deleteFlow(flow2);
        }
    }

    @Test
    protected void shouldFindAllWithSourceForAllTenants() {
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());

        FlowWithSource flow1 = flowRepository.create(createTestingLogFlow(tenant1, IdUtils.create(), "first"));
        FlowWithSource flow2 = flowRepository.create(createTestingLogFlow(tenant2, IdUtils.create(), "second"));

        try {
            List<FlowWithSource> results = flowRepository.findAllWithSourceForAllTenants();
            assertThat(results.stream().map(FlowWithSource::getId).toList())
                .contains(flow1.getId(), flow2.getId());
            // verify source is populated
            assertThat(results.stream()
                .filter(f -> f.getId().equals(flow1.getId()) || f.getId().equals(flow2.getId()))
                .toList()
            ).allMatch(f -> f.getSource() != null);
        } finally {
            deleteFlow(flow1);
            deleteFlow(flow2);
        }
    }

    private static Flow createTestFlowForNamespace(String tenantId, String namespace) {
        return Flow.builder()
            .id(IdUtils.create())
            .namespace(namespace)
            .tenantId(tenantId)
            .tasks(
                List.of(
                    Return.builder()
                        .id(IdUtils.create())
                        .type(Return.class.getName())
                        .build()
                )
            )
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
            if (
                (event.getModel() != null && event.getModel() instanceof AbstractFlow) ||
                    (event.getPreviousModel() != null && event.getPreviousModel() instanceof AbstractFlow)
            ) {
                emits.add(event);
            }
        }

        public static void reset() {
            emits = new CopyOnWriteArrayList<>();
        }

        public static List<CrudEvent<AbstractFlow>> filterByTenant(String tenantId) {
            return emits.stream()
                .filter(
                    e -> (e.getPreviousModel() != null && e.getPreviousModel().getTenantId().equals(tenantId)) ||
                        (e.getModel() != null && e.getModel().getTenantId().equals(tenantId))
                )
                .toList();
        }
    }

    protected static GenericFlow createTestingLogFlow(String tenantId, String id, String logMessage) {
        return createTestingLogFlow(tenantId, id, logMessage, false);
    }

    protected static GenericFlow createTestingLogFlow(String tenantId, String id, String logMessage, boolean draft) {
        String source = """
               id: %s
               namespace: %s
               draft: %s
               tasks:
                 - id: log
                   type: io.kestra.plugin.core.log.Log
                   message: %s
            """.formatted(id, TEST_NAMESPACE, draft, logMessage);
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
                    .trigger(
                        ExecutionTrigger.builder()
                            .id(this.getId())
                            .type(this.getType())
                            .variables(
                                ImmutableMap.of(
                                    "counter", COUNTER,
                                    "defaultInjected", defaultInjected == null ? "ko" : defaultInjected
                                )
                            )
                            .build()
                    )
                    .build();

                return Optional.of(execution);
            }
        }
    }
}
