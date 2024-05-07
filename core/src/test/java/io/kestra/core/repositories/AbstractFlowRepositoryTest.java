package io.kestra.core.repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import io.kestra.core.Helpers;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.schedulers.AbstractSchedulerTest;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.TaskDefaultService;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.tasks.flows.Template;
import io.kestra.core.tasks.log.Log;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
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

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractFlowRepositoryTest {
    @Inject
    protected FlowRepositoryInterface flowRepository;

    @Inject
    private LocalFlowRepositoryLoader repositoryLoader;

    @Inject
    protected TaskDefaultService taskDefaultService;

    @Inject
    @Named(QueueFactoryInterface.TRIGGER_NAMED)
    private QueueInterface<Trigger> triggerQueue;

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
            .tasks(Collections.singletonList(Return.builder().id(taskId).type(Return.class.getName()).format("test").build()));
    }

    @Test
    void findById() {
        Flow flow = builder()
            .revision(3)
            .build();
        flowRepository.create(flow, flow.generateSource(), taskDefaultService.injectDefaults(flow));

        Optional<Flow> full = flowRepository.findById(null, flow.getNamespace(), flow.getId());
        assertThat(full.isPresent(), is(true));
        assertThat(full.get().getRevision(), is(1));

        full = flowRepository.findById(null, flow.getNamespace(), flow.getId(), Optional.empty());
        assertThat(full.isPresent(), is(true));
    }

    @Test
    void findByIdWithoutAcl() {
        Flow flow = builder()
            .revision(3)
            .build();
        flowRepository.create(flow, flow.generateSource(), taskDefaultService.injectDefaults(flow));

        Optional<Flow> full = flowRepository.findByIdWithoutAcl(null, flow.getNamespace(), flow.getId(), Optional.empty());
        assertThat(full.isPresent(), is(true));
        assertThat(full.get().getRevision(), is(1));

        full = flowRepository.findByIdWithoutAcl(null, flow.getNamespace(), flow.getId(), Optional.empty());
        assertThat(full.isPresent(), is(true));
    }

    @Test
    void findByIdWithSource() {
        Flow flow = builder()
            .revision(3)
            .build();
        flowRepository.create(flow, "# comment\n" + flow.generateSource(), taskDefaultService.injectDefaults(flow));

        Optional<FlowWithSource> full = flowRepository.findByIdWithSource(null, flow.getNamespace(), flow.getId());
        assertThat(full.isPresent(), is(true));

        full.ifPresent(current -> {
            assertThat(full.get().getRevision(), is(1));
            assertThat(full.get().getSource(), containsString("# comment"));
            assertThat(full.get().getSource(), not(containsString("revision:")));
        });
    }

    @Test
    protected void revision() throws JsonProcessingException {
        String flowId = IdUtils.create();
        // create with builder
        Flow first = Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .inputs(ImmutableList.of(StringInput.builder().type(Type.STRING).id("a").build()))
            .build();
        // create with repository
        FlowWithSource flow = flowRepository.create(first, first.generateSource(), taskDefaultService.injectDefaults(first));

        // submit new one, no change
        Flow notSaved = flowRepository.update(flow, flow, first.generateSource(), taskDefaultService.injectDefaults(flow));
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
        FlowWithSource incremented = flowRepository.update(flowRev2, flow, flowRev2.generateSource(), taskDefaultService.injectDefaults(flowRev2));
        assertThat(incremented.getRevision(), is(2));

        // revision is well saved
        List<FlowWithSource> revisions = flowRepository.findRevisions(null, flow.getNamespace(), flow.getId());
        assertThat(revisions.size(), is(2));

        // submit the same one serialized, no changed
        FlowWithSource incremented2 = flowRepository.update(
            JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(flowRev2), Flow.class),
            flowRev2,
            JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(flowRev2), Flow.class).generateSource(),
            taskDefaultService.injectDefaults(flowRev2)
        );
        assertThat(incremented2.getRevision(), is(2));

        // resubmit first one, revision is incremented
        FlowWithSource incremented3 = flowRepository.update(
            JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(flow.toFlow()), Flow.class),
            flowRev2,
            JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(flow.toFlow()), Flow.class).generateSource(),
            taskDefaultService.injectDefaults(JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(flow.toFlow()), Flow.class))
        );
        assertThat(incremented3.getRevision(), is(3));

        // delete
        flowRepository.delete(incremented3);

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
        Flow incremented4 = flowRepository.create(flow, flow.generateSource(), taskDefaultService.injectDefaults(flow));

        assertThat(incremented4.getRevision(), is(5));
    }

    @Test
    void save() {
        Flow flow = builder().revision(12).build();
        Flow save = flowRepository.create(flow, flow.generateSource(), taskDefaultService.injectDefaults(flow));

        assertThat(save.getRevision(), is(1));
    }

    @Test
    void saveNoRevision() {
        Flow flow = builder().build();
        Flow save = flowRepository.create(flow, flow.generateSource(), taskDefaultService.injectDefaults(flow));

        assertThat(save.getRevision(), is(1));

        flowRepository.delete(save);
    }

    @Test
    void findAll() {
        List<Flow> save = flowRepository.findAll(null);

        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT));
    }

    @Test
    void findAllForAllTenants() {
        List<Flow> save = flowRepository.findAllForAllTenants();

        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT));
    }

    @Test
    void findByNamespace() {
        List<Flow> save = flowRepository.findByNamespace(null, "io.kestra.tests");
        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT - 15));

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
        flowRepository.create(flow, flowSource, taskDefaultService.injectDefaults(flow));

        List<FlowWithSource> save = flowRepository.findByNamespaceWithSource(null, flow.getNamespace());
        assertThat((long) save.size(), is(1L));

        assertThat(save.get(0).getSource(), is(FlowService.cleanupSource(flowSource)));
    }

    @Test
    void find() {
        List<Flow> save = flowRepository.find(Pageable.from(1, (int) Helpers.FLOWS_COUNT - 1, Sort.UNSORTED), null, null, null, null);
        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT - 1));

        save = flowRepository.find(Pageable.from(1, (int) Helpers.FLOWS_COUNT + 1, Sort.UNSORTED), null, null, null, null);
        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT));

        save = flowRepository.find(Pageable.from(1),null, null, "io.kestra.tests.minimal.bis", Collections.emptyMap());
        assertThat((long) save.size(), is(1L));

        save = flowRepository.find(Pageable.from(1, 100, Sort.UNSORTED), null, null, null, Map.of("country", "FR"));
        assertThat(save.size(), is(1));

        save = flowRepository.find(Pageable.from(1),null, null, "io.kestra.tests", Map.of("key2", "value2"));
        assertThat((long) save.size(), is(1L));

        save = flowRepository.find(Pageable.from(1),null, null, "io.kestra.tests", Map.of("key1", "value2"));
        assertThat((long) save.size(), is(0L));
    }

    @Test
    void findWithSource() {
        List<FlowWithSource> save = flowRepository.findWithSource(null, null, "io.kestra.tests", Collections.emptyMap());
        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT - 1));

        save = flowRepository.findWithSource(null, null, "io.kestra.tests2", Collections.emptyMap());
        assertThat((long) save.size(), is(1L));

        save = flowRepository.findWithSource(null, null, "io.kestra.tests.minimal.bis", Collections.emptyMap());
        assertThat((long) save.size(), is(1L));
    }

    @Test
    void delete() {
        Flow flow = builder().build();

        Flow save = flowRepository.create(flow, flow.generateSource(), taskDefaultService.injectDefaults(flow));
        assertThat(flowRepository.findById(null, save.getNamespace(), save.getId()).isPresent(), is(true));

        Flow delete = flowRepository.delete(save);

        assertThat(flowRepository.findById(null, flow.getNamespace(), flow.getId()).isPresent(), is(false));
        assertThat(flowRepository.findById(null, flow.getNamespace(), flow.getId(), Optional.of(save.getRevision())).isPresent(), is(true));

        List<FlowWithSource> revisions = flowRepository.findRevisions(null, flow.getNamespace(), flow.getId());
        assertThat(revisions.get(revisions.size() - 1).getRevision(), is(delete.getRevision()));
    }

    @Test
    void updateConflict() {
        String flowId = IdUtils.create();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .inputs(ImmutableList.of(StringInput.builder().type(Type.STRING).id("a").build()))
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();

        Flow save = flowRepository.create(flow, flow.generateSource(), taskDefaultService.injectDefaults(flow));

        assertThat(flowRepository.findById(null, flow.getNamespace(), flow.getId()).isPresent(), is(true));

        Flow update = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest2")
            .inputs(ImmutableList.of(StringInput.builder().type(Type.STRING).id("b").build()))
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();
        ;

        ConstraintViolationException e = assertThrows(
            ConstraintViolationException.class,
            () -> flowRepository.update(update, flow, update.generateSource(), taskDefaultService.injectDefaults(update))
        );

        assertThat(e.getConstraintViolations().size(), is(2));

        flowRepository.delete(save);
    }

    @Test
    void removeTrigger() throws TimeoutException {
        String flowId = IdUtils.create();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .triggers(Collections.singletonList(AbstractSchedulerTest.UnitTest.builder()
                .id("sleep")
                .type(AbstractSchedulerTest.UnitTest.class.getName())
                .build()))
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();

        flowRepository.create(flow, flow.generateSource(), taskDefaultService.injectDefaults(flow));
        assertThat(flowRepository.findById(null, flow.getNamespace(), flow.getId()).isPresent(), is(true));

        Flow update = Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();
        ;

        Flow updated = flowRepository.update(update, flow, update.generateSource(), taskDefaultService.injectDefaults(update));
        assertThat(updated.getTriggers(), is(nullValue()));

        flowRepository.delete(updated);

        Await.until(() -> FlowListener.getEmits().size() == 3, Duration.ofMillis(100), Duration.ofSeconds(5));
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.CREATE).count(), is(1L));
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.UPDATE).count(), is(1L));
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.DELETE).count(), is(1L));
    }


    @Test
    void removeTriggerDelete() throws TimeoutException {
        String flowId = IdUtils.create();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .triggers(Collections.singletonList(AbstractSchedulerTest.UnitTest.builder()
                .id("sleep")
                .type(AbstractSchedulerTest.UnitTest.class.getName())
                .build()))
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();

        Flow save = flowRepository.create(flow, flow.generateSource(), taskDefaultService.injectDefaults(flow));

        assertThat(flowRepository.findById(null, flow.getNamespace(), flow.getId()).isPresent(), is(true));

        flowRepository.delete(save);

        Await.until(() -> FlowListener.getEmits().size() == 2, Duration.ofMillis(100), Duration.ofSeconds(5));
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.CREATE).count(), is(1L));
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.DELETE).count(), is(1L));
    }

    @Test
    void findDistinctNamespace() {
        List<String> distinctNamespace = flowRepository.findDistinctNamespace(null);
        assertThat((long) distinctNamespace.size(), is(5L));
    }

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

        flowRepository.create(
            flow,
            flow.generateSource(),
            flow
        );

        Optional<Flow> found = flowRepository.findById(null, flow.getNamespace(), flow.getId());

        assertThat(found.isPresent(), is(true));
        assertThat(found.get() instanceof FlowWithException, is(true));
        assertThat(((FlowWithException) found.get()).getException(), containsString("Templates are disabled"));
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
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .inputs(ImmutableList.of(StringInput.builder().type(Type.STRING).id("a").build()))
            .build();
        // create with repository
        flowRepository.create(first, first.generateSource(), taskDefaultService.injectDefaults(first));
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

        flowRepository.update(flowRev2, first, flowRev2.generateSource(), taskDefaultService.injectDefaults(flowRev2));
        assertThat(flowRepository.lastRevision(tenantId, namespace, flowId), is(2));
    }

    @Singleton
    public static class FlowListener implements ApplicationEventListener<CrudEvent<Flow>> {
        private static List<CrudEvent<Flow>> emits = new ArrayList<>();

        @Override
        public void onApplicationEvent(CrudEvent<Flow> event) {
            emits.add(event);
        }

        public static List<CrudEvent<Flow>> getEmits() {
            return emits;
        }

        public static void reset() {
            emits = new ArrayList<>();
        }
    }
}
