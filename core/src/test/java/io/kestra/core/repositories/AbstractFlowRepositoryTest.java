package io.kestra.core.repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import io.kestra.core.Helpers;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.schedulers.AbstractSchedulerTest;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.flow.Template;
import io.kestra.plugin.core.log.Log;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

// If some counts are wrong in this test it means that one of the tests is not properly deleting what it created
@KestraTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractFlowRepositoryTest {
    @Inject
    protected FlowRepositoryInterface flowRepository;

    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    @Inject
    private LocalFlowRepositoryLoader repositoryLoader;

    @Inject
    protected PluginDefaultService pluginDefaultService;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        TestsUtils.loads(repositoryLoader);
        FlowListener.reset();
    }

    private static Flow.FlowBuilder<?, ?> builder() {
        return builder(IdUtils.create(), "test");
    }

    private static Flow.FlowBuilder<?, ?> builder(String flowId, String taskId) {
        return Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .tasks(Collections.singletonList(Return.builder().id(taskId).type(Return.class.getName()).format(Property.of("test")).build()));
    }

    @Test
    void findById() {
        Flow flow = builder()
            .revision(3)
            .build();
        flow = flowRepository.create(flow, flow.generateSource(), pluginDefaultService.injectDefaults(flow.withSource(flow.generateSource())));
        try {
            Optional<Flow> full = flowRepository.findById(null, flow.getNamespace(), flow.getId());
            assertThat(full.isPresent(), is(true));
            assertThat(full.get().getRevision(), is(1));

            full = flowRepository.findById(null, flow.getNamespace(), flow.getId(), Optional.empty());
            assertThat(full.isPresent(), is(true));
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    void findByIdWithoutAcl() {
        Flow flow = builder()
            .revision(3)
            .build();
        flow = flowRepository.create(flow, flow.generateSource(), pluginDefaultService.injectDefaults(flow.withSource(flow.generateSource())));
        try {
            Optional<Flow> full = flowRepository.findByIdWithoutAcl(null, flow.getNamespace(), flow.getId(), Optional.empty());
            assertThat(full.isPresent(), is(true));
            assertThat(full.get().getRevision(), is(1));

            full = flowRepository.findByIdWithoutAcl(null, flow.getNamespace(), flow.getId(), Optional.empty());
            assertThat(full.isPresent(), is(true));
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    void findByIdWithSource() {
        Flow flow = builder()
            .revision(3)
            .build();
        flow = flowRepository.create(flow, "# comment\n" + flow.generateSource(), pluginDefaultService.injectDefaults(flow.withSource(flow.generateSource())));

        try {
            Optional<FlowWithSource> full = flowRepository.findByIdWithSource(null, flow.getNamespace(), flow.getId());
            assertThat(full.isPresent(), is(true));

            full.ifPresent(current -> {
                assertThat(full.get().getRevision(), is(1));
                assertThat(full.get().getSource(), containsString("# comment"));
                assertThat(full.get().getSource(), not(containsString("revision:")));
            });
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    protected void revision() throws JsonProcessingException {
        String flowId = IdUtils.create();
        // create with builder
        Flow first = Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format(Property.of("test")).build()))
            .inputs(ImmutableList.of(StringInput.builder().type(Type.STRING).id("a").build()))
            .build();
        // create with repository
        FlowWithSource flow = flowRepository.create(first, first.generateSource(), pluginDefaultService.injectDefaults(first.withSource(first.generateSource())));

        List<FlowWithSource> revisions;
        try {
            // submit new one, no change
            Flow notSaved = flowRepository.update(flow, flow, first.generateSource(), pluginDefaultService.injectDefaults(flow));
            assertThat(notSaved.getRevision(), is(flow.getRevision()));

            // submit new one with change
            Flow flowRev2 = Flow.builder()
                .id(flowId)
                .namespace("io.kestra.unittest")
                .tasks(Collections.singletonList(
                    Log.builder()
                        .id(IdUtils.create())
                        .type(Log.class.getName())
                        .message("Hello World")
                        .build()
                ))
                .inputs(ImmutableList.of(StringInput.builder().type(Type.STRING).id("b").build()))
                .build();

            // revision is incremented
            FlowWithSource incremented = flowRepository.update(flowRev2, flow, flowRev2.generateSource(), pluginDefaultService.injectDefaults(flowRev2.withSource(flowRev2.generateSource())));
            assertThat(incremented.getRevision(), is(2));

            // revision is well saved
            revisions = flowRepository.findRevisions(null, flow.getNamespace(), flow.getId());
            assertThat(revisions.size(), is(2));

            // submit the same one serialized, no changed
            FlowWithSource incremented2 = flowRepository.update(
                JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(flowRev2), Flow.class),
                flowRev2,
                JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(flowRev2), Flow.class).generateSource(),
                pluginDefaultService.injectDefaults(flowRev2.withSource(flowRev2.generateSource()))
            );
            assertThat(incremented2.getRevision(), is(2));

            // resubmit first one, revision is incremented
            FlowWithSource incremented3 = flowRepository.update(
                JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(flow.toFlow()), Flow.class),
                flowRev2,
                JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(flow.toFlow()), Flow.class).generateSource(),
                pluginDefaultService.injectDefaults(JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(flow.toFlow()), Flow.class).withSource(flow.getSource()))
            );
            assertThat(incremented3.getRevision(), is(3));
        } finally {
            deleteFlow(flow);
        }

        // revisions is still findable after delete
        revisions = flowRepository.findRevisions(null, flow.getNamespace(), flow.getId());
        assertThat(revisions.size(), is(4));


        Optional<Flow> findDeleted = flowRepository.findById(
            null,
            flow.getNamespace(),
            flow.getId(),
            Optional.of(flow.getRevision())
        );
        assertThat(findDeleted.isPresent(), is(true));
        assertThat(findDeleted.get().getRevision(), is(flow.getRevision()));

        // recreate the first one, we have a new revision
        Flow incremented4 = flowRepository.create(flow, flow.generateSource(), pluginDefaultService.injectDefaults(flow));

        try {
            assertThat(incremented4.getRevision(), is(5));
        } finally {
            deleteFlow(incremented4);
        }
    }

    @Test
    void save() {
        Flow flow = builder().revision(12).build();
        Flow save = flowRepository.create(flow, flow.generateSource(), pluginDefaultService.injectDefaults(flow.withSource(flow.generateSource())));

        try {
            assertThat(save.getRevision(), is(1));
        } finally {
            deleteFlow(save);
        }
    }

    @Test
    void saveNoRevision() {
        Flow flow = builder().build();
        Flow save = flowRepository.create(flow, flow.generateSource(), pluginDefaultService.injectDefaults(flow.withSource(flow.generateSource())));

        try {
            assertThat(save.getRevision(), is(1));
        } finally {
            deleteFlow(save);
        }

    }

    @Test
    void findAll() {
        List<Flow> save = flowRepository.findAll(null);

        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT));
    }

    @Test
    void findAllWithSource() {
        List<FlowWithSource> save = flowRepository.findAllWithSource(null);

        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT));
    }

    @Test
    void findAllForAllTenants() {
        List<Flow> save = flowRepository.findAllForAllTenants();

        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT));
    }

    @Test
    void findAllWithSourceForAllTenants() {
        List<FlowWithSource> save = flowRepository.findAllWithSourceForAllTenants();

        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT));
    }

    @Test
    void findByNamespace() {
        List<Flow> save = flowRepository.findByNamespace(null, "io.kestra.tests");
        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT - 20));

        save = flowRepository.findByNamespace(null, "io.kestra.tests2");
        assertThat((long) save.size(), is(1L));

        save = flowRepository.findByNamespace(null, "io.kestra.tests.minimal.bis");
        assertThat((long) save.size(), is(1L));
    }

    @Test
    void findByNamespacePrefix() {
        List<Flow> save = flowRepository.findByNamespacePrefix(null, "io.kestra.tests");
        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT - 1));

        save = flowRepository.findByNamespace(null, "io.kestra.tests2");
        assertThat((long) save.size(), is(1L));

        save = flowRepository.findByNamespace(null, "io.kestra.tests.minimal.bis");
        assertThat((long) save.size(), is(1L));
    }

    @Test
    void findByNamespaceWithSource() {
        Flow flow = builder()
            .revision(3)
            .build();
        String flowSource = "# comment\n" + flow.generateSource();
        flow = flowRepository.create(flow, flowSource, pluginDefaultService.injectDefaults(flow.withSource(flowSource)));

        try {
            List<FlowWithSource> save = flowRepository.findByNamespaceWithSource(null, flow.getNamespace());
            assertThat((long) save.size(), is(1L));

            assertThat(save.getFirst().getSource(), is(FlowService.cleanupSource(flowSource)));
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    protected void find() {
        List<Flow> save = flowRepository.find(Pageable.from(1, (int) Helpers.FLOWS_COUNT - 1, Sort.UNSORTED), null, null, null, null, null);
        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT - 1));

        save = flowRepository.find(Pageable.from(1, (int) Helpers.FLOWS_COUNT + 1, Sort.UNSORTED), null, null, null, null, null);
        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT));

        save = flowRepository.find(Pageable.from(1), null, null, null, "io.kestra.tests.minimal.bis", Collections.emptyMap());
        assertThat((long) save.size(), is(1L));

        save = flowRepository.find(Pageable.from(1, 100, Sort.UNSORTED), null, null, null, null, Map.of("country", "FR"));
        assertThat(save.size(), is(1));

        save = flowRepository.find(Pageable.from(1), null, null, null, "io.kestra.tests", Map.of("key2", "value2"));
        assertThat((long) save.size(), is(1L));

        save = flowRepository.find(Pageable.from(1), null, null, null, "io.kestra.tests", Map.of("key1", "value2"));
        assertThat((long) save.size(), is(0L));
    }

    @Test
    protected void findSpecialChars() {
        ArrayListTotal<SearchResult<Flow>> save = flowRepository.findSourceCode(Pageable.unpaged(), "https://api.chucknorris.io", null, null);
        assertThat((long) save.size(), is(2L));
    }

    @Test
    void findWithSource() {
        List<FlowWithSource> save = flowRepository.findWithSource(null, null, null, "io.kestra.tests", Collections.emptyMap());
        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT - 1));

        save = flowRepository.findWithSource(null, null, null, "io.kestra.tests2", Collections.emptyMap());
        assertThat((long) save.size(), is(1L));

        save = flowRepository.findWithSource(null, null, null, "io.kestra.tests.minimal.bis", Collections.emptyMap());
        assertThat((long) save.size(), is(1L));
    }

    @Test
    void delete() {
        Flow flow = builder().build();

        FlowWithSource save = flowRepository.create(flow, flow.generateSource(), pluginDefaultService.injectDefaults(flow.withSource(flow.generateSource())));

        try {
            assertThat(flowRepository.findById(null, save.getNamespace(), save.getId()).isPresent(), is(true));
        } catch (Throwable e) {
            deleteFlow(save);
            throw e;
        }

        Flow delete = flowRepository.delete(save);

        assertThat(flowRepository.findById(null, flow.getNamespace(), flow.getId()).isPresent(), is(false));
        assertThat(flowRepository.findById(null, flow.getNamespace(), flow.getId(), Optional.of(save.getRevision())).isPresent(), is(true));

        List<FlowWithSource> revisions = flowRepository.findRevisions(null, flow.getNamespace(), flow.getId());
        assertThat(revisions.getLast().getRevision(), is(delete.getRevision()));
    }

    @Test
    void updateConflict() {
        String flowId = IdUtils.create();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .inputs(ImmutableList.of(StringInput.builder().type(Type.STRING).id("a").build()))
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format(Property.of("test")).build()))
            .build();

        Flow save = flowRepository.create(flow, flow.generateSource(), pluginDefaultService.injectDefaults(flow.withSource(flow.generateSource())));

        try {
            assertThat(flowRepository.findById(null, flow.getNamespace(), flow.getId()).isPresent(), is(true));

            Flow update = Flow.builder()
                .id(IdUtils.create())
                .namespace("io.kestra.unittest2")
                .inputs(ImmutableList.of(StringInput.builder().type(Type.STRING).id("b").build()))
                .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format(Property.of("test")).build()))
                .build();
            ;

            ConstraintViolationException e = assertThrows(
                ConstraintViolationException.class,
                () -> flowRepository.update(update, flow, update.generateSource(), pluginDefaultService.injectDefaults(update.withSource(update.generateSource())))
            );

            assertThat(e.getConstraintViolations().size(), is(2));
        } finally {
            deleteFlow(save);
        }
    }

    @Test
    void removeTrigger() throws TimeoutException, QueueException {
        String flowId = IdUtils.create();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .triggers(Collections.singletonList(AbstractSchedulerTest.UnitTest.builder()
                .id("sleep")
                .type(AbstractSchedulerTest.UnitTest.class.getName())
                .build()))
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format(Property.of("test")).build()))
            .build();

        flow = flowRepository.create(flow, flow.generateSource(), pluginDefaultService.injectDefaults(flow.withSource(flow.generateSource())));
        try {
            assertThat(flowRepository.findById(null, flow.getNamespace(), flow.getId()).isPresent(), is(true));

            Flow update = Flow.builder()
                .id(flowId)
                .namespace("io.kestra.unittest")
                .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format(Property.of("test")).build()))
                .build();
            ;

            Flow updated = flowRepository.update(update, flow, update.generateSource(), pluginDefaultService.injectDefaults(update.withSource(update.generateSource())));
            assertThat(updated.getTriggers(), is(nullValue()));
        } finally {
            deleteFlow(flow);
        }

        Await.until(() -> FlowListener.getEmits().size() == 3, Duration.ofMillis(100), Duration.ofSeconds(5));
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.CREATE).count(), is(1L));
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.UPDATE).count(), is(1L));
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.DELETE).count(), is(1L));
    }


    @Test
    void removeTriggerDelete() throws TimeoutException, QueueException {
        String flowId = IdUtils.create();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .triggers(Collections.singletonList(AbstractSchedulerTest.UnitTest.builder()
                .id("sleep")
                .type(AbstractSchedulerTest.UnitTest.class.getName())
                .build()))
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format(Property.of("test")).build()))
            .build();

        Flow save = flowRepository.create(flow, flow.generateSource(), pluginDefaultService.injectDefaults(flow.withSource(flow.generateSource())));
        try {
            assertThat(flowRepository.findById(null, flow.getNamespace(), flow.getId()).isPresent(), is(true));
        } finally {
            deleteFlow(save);
        }

        Await.until(() -> FlowListener.getEmits().size() == 2, Duration.ofMillis(100), Duration.ofSeconds(5));
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.CREATE).count(), is(1L));
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.DELETE).count(), is(1L));
    }

    @Test
    void findDistinctNamespace() {
        List<String> distinctNamespace = flowRepository.findDistinctNamespace(null);
        assertThat((long) distinctNamespace.size(), is(7L));
    }

    @SuppressWarnings("deprecation")
    @Test
    void templateDisabled() {
        Template template = Template.builder()
            .id(IdUtils.create())
            .type(Template.class.getName())
            .namespace("test")
            .templateId("testTemplate")
            .build();

        Template templateSpy = spy(template);

        doReturn(Collections.emptyList())
            .when(templateSpy)
            .allChildTasks();

        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .tasks(Collections.singletonList(templateSpy))
            .build();

        flow = flowRepository.create(
            flow,
            flow.generateSource(),
            flow
        );

        try {
            Optional<Flow> found = flowRepository.findById(null, flow.getNamespace(), flow.getId());

            assertThat(found.isPresent(), is(true));
            assertThat(found.get() instanceof FlowWithException, is(true));
            assertThat(((FlowWithException) found.get()).getException(), containsString("Templates are disabled"));
        } finally {
            deleteFlow(flow);
        }
    }

    @Test
    protected void lastRevision() {
        String namespace = "io.kestra.unittest";
        String flowId = IdUtils.create();
        String tenantId = "tenant";

        assertThat(flowRepository.lastRevision(tenantId, namespace, flowId), nullValue());

        // create with builder
        Flow first = Flow.builder()
            .tenantId(tenantId)
            .id(flowId)
            .namespace(namespace)
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format(Property.of("test")).build()))
            .inputs(ImmutableList.of(StringInput.builder().type(Type.STRING).id("a").build()))
            .build();
        // create with repository
        first = flowRepository.create(first, first.generateSource(), pluginDefaultService.injectDefaults(first.withSource(first.generateSource())));
        try {
            assertThat(flowRepository.lastRevision(tenantId, namespace, flowId), is(1));

            // submit new one with change

            Flow flowRev2 = first.toBuilder()
                .tasks(Collections.singletonList(
                    Log.builder()
                        .id(IdUtils.create())
                        .type(Log.class.getName())
                        .message("Hello World")
                        .build()
                ))
                .inputs(ImmutableList.of(StringInput.builder().type(Type.STRING).id("b").build()))
                .build();

            first = flowRepository.update(flowRev2, first, flowRev2.generateSource(), pluginDefaultService.injectDefaults(flowRev2.withSource(flowRev2.generateSource())));
            assertThat(flowRepository.lastRevision(tenantId, namespace, flowId), is(2));
        } finally {
            deleteFlow(first);
        }
    }

    @Test
    void findByExecution() {
        Flow flow = builder()
            .revision(1)
            .build();
        flowRepository.create(flow, flow.generateSource(), pluginDefaultService.injectDefaults(flow));
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .state(new State())
            .build();
        execution = executionRepository.save(execution);

        try {
            Flow full = flowRepository.findByExecution(execution);
            assertThat(full, notNullValue());
            assertThat(full.getNamespace(), is(flow.getNamespace()));
            assertThat(full.getId(), is(flow.getId()));

            full = flowRepository.findByExecutionWithoutAcl(execution);
            assertThat(full, notNullValue());
            assertThat(full.getNamespace(), is(flow.getNamespace()));
            assertThat(full.getId(), is(flow.getId()));
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
        flowRepository.create(flow, flow.generateSource(), pluginDefaultService.injectDefaults(flow));
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .state(new State())
            .build();
        executionRepository.save(execution);

        try {
            Flow full = flowRepository.findByExecution(execution);
            assertThat(full, notNullValue());
            assertThat(full.getNamespace(), is(flow.getNamespace()));
            assertThat(full.getId(), is(flow.getId()));

            full = flowRepository.findByExecutionWithoutAcl(execution);
            assertThat(full, notNullValue());
            assertThat(full.getNamespace(), is(flow.getNamespace()));
            assertThat(full.getId(), is(flow.getId()));
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
            Flow flow = createTestFlowForNamespace("io.kestra.unittest");
            toDelete = flowRepository.create(flow, "", flow);
            // When
            int count = flowRepository.count(null);

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
            toDelete.add(flowRepository.create(createTestFlowForNamespace("io.kestra.unittest.sub"), "", createTestFlowForNamespace("io.kestra.unittest.sub")));
            toDelete.add(flowRepository.create(createTestFlowForNamespace("io.kestra.unittest.shouldcountbynamespacefornulltenant"), "", createTestFlowForNamespace("io.kestra.unittest.shouldcountbynamespacefornulltenant")));
            toDelete.add(flowRepository.create(createTestFlowForNamespace("com.kestra.unittest"), "", createTestFlowForNamespace("com.kestra.unittest")));

            int count = flowRepository.countForNamespace(null, "io.kestra.unittest.shouldcountbynamespacefornulltenant");
            assertThat(count, is(1));

            count = flowRepository.countForNamespace(null, "io.kestra.unittest");
            assertThat(count, is(2));
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
            .tasks(List.of(Return.builder()
                .id(IdUtils.create())
                .type(Return.class.getName())
                .build()
            ))
            .build();
    }

    private void deleteFlow(Flow flow) {
        Integer revision = flowRepository.lastRevision(flow.getTenantId(), flow.getNamespace(), flow.getId());
        flowRepository.delete(flow.toBuilder().revision(revision).build().withSource(flow.generateSource()));
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
}
