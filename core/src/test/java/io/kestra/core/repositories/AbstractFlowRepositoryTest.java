package io.kestra.core.repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import io.kestra.core.Helpers;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.schedulers.AbstractSchedulerTest;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.tasks.scripts.Bash;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.validation.ConstraintViolationException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(transactional = false)
public abstract class AbstractFlowRepositoryTest {
    @Inject
    protected FlowRepositoryInterface flowRepository;

    @Inject
    private LocalFlowRepositoryLoader repositoryLoader;

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
        flowRepository.create(flow);

        Optional<Flow> full = flowRepository.findById(flow.getNamespace(), flow.getId());
        assertThat(full.isPresent(), is(true));

        full.ifPresent(current -> {
            assertThat(full.get().getRevision(), is(1));
        });
    }

    @Test
    protected void revision() throws JsonProcessingException {
        String flowId = IdUtils.create();

        // create
        Flow flow = flowRepository.create(Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .inputs(ImmutableList.of(Input.builder().type(Input.Type.STRING).name("a").build()))
            .build());

        // submit new one, no change
        Flow notSaved = flowRepository.update(flow, flow);
        assertThat(flow.getRevision(), is(notSaved.getRevision()));

        // submit new one with change
        Flow flowRev2 = Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .tasks(Collections.singletonList(
                Bash.builder()
                    .id("id")
                    .type(Bash.class.getName())
                    .commands(Collections.singletonList("echo 1").toArray(new String[0]))
                    .build()
            ))
            .inputs(ImmutableList.of(Input.builder().type(Input.Type.STRING).name("b").build()))
            .build();

        // revision is incremented
        Flow incremented = flowRepository.update(flowRev2, flow);
        assertThat(incremented.getRevision(), is(2));

        // revision is well saved
        List<Flow> revisions = flowRepository.findRevisions(flow.getNamespace(), flow.getId());
        assertThat(revisions.size(), is(2));

        // submit the same one serialized, no changed
        Flow incremented2 = flowRepository.update(
            JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(flowRev2), Flow.class),
            flowRev2
        );
        assertThat(incremented2.getRevision(), is(2));

        // resubmit first one, revision is incremented
        Flow incremented3 = flowRepository.update(
            JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(flow), Flow.class),
            flowRev2
        );
        assertThat(incremented3.getRevision(), is(3));

        // delete
        flowRepository.delete(incremented3);

        // revisions is still findable after delete
        revisions = flowRepository.findRevisions(flow.getNamespace(), flow.getId());
        assertThat(revisions.size(), is(4));

        Optional<Flow> findDeleted = flowRepository.findById(
            flow.getNamespace(),
            flow.getId(),
            Optional.of(flow.getRevision())
        );
        assertThat(findDeleted.isPresent(), is(true));
        assertThat(findDeleted.get().getRevision(), is(flow.getRevision()));

        // recreate the first one, we have a new revision
        Flow incremented4 = flowRepository.create(flow);

        assertThat(incremented4.getRevision(), is(5));
    }

    @Test
    void save() {
        Flow flow = builder().revision(12).build();
        Flow save = flowRepository.create(flow);

        assertThat(save.getRevision(), is(1));
    }

    @Test
    void saveNoRevision() {
        Flow flow = builder().build();
        Flow save = flowRepository.create(flow);

        assertThat(save.getRevision(), is(1));

        flowRepository.delete(save);
    }

    @Test
    void findAll() {
        List<Flow> save = flowRepository.findAll();

        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT));
    }

    @Test
    void findAllWithRevisions() {
        String flowId = "findall_" + IdUtils.create();

        flowRepository.create(builder(flowId, "test").build());
        flowRepository.create(builder(flowId, "test1").build());
        Flow last = flowRepository.create(builder(flowId, "test2").build());
        flowRepository.delete(last);

        List<Flow> allWithRevisions = flowRepository.findAllWithRevisions();
        assertThat(allWithRevisions.stream().filter(flow -> flow.getId().equals(flowId)).count(), is(4L));
    }

    @Test
    void findByNamespace() {
        List<Flow> save = flowRepository.findByNamespace("io.kestra.tests");
        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT - 1));

        save = flowRepository.findByNamespace("io.kestra.tests.minimal.bis");
        assertThat((long) save.size(), is(1L));
    }

    @Test
    void delete() {
        Flow flow = builder().build();

        Flow save = flowRepository.create(flow);
        assertThat(flowRepository.findById(save.getNamespace(), save.getId()).isPresent(), is(true));

        Flow delete = flowRepository.delete(save);

        assertThat(flowRepository.findById(flow.getNamespace(), flow.getId()).isPresent(), is(false));
        assertThat(flowRepository.findById(flow.getNamespace(), flow.getId(), Optional.of(save.getRevision())).isPresent(), is(true));

        List<Flow> revisions = flowRepository.findRevisions(flow.getNamespace(), flow.getId());
        assertThat(revisions.get(revisions.size() - 1).getRevision(), is(delete.getRevision()));
    }

    @Test
    void updateConflict() {
        String flowId = IdUtils.create();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .inputs(ImmutableList.of(Input.builder().type(Input.Type.STRING).name("a").build()))
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();

        Flow save = flowRepository.create(flow);

        assertThat(flowRepository.findById(flow.getNamespace(), flow.getId()).isPresent(), is(true));

        Flow update = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest2")
            .inputs(ImmutableList.of(Input.builder().type(Input.Type.STRING).name("b").build()))
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();
        ;

        ConstraintViolationException e = assertThrows(
            ConstraintViolationException.class,
            () -> flowRepository.update(update, flow)
        );

        assertThat(e.getConstraintViolations().size(), is(2));

        flowRepository.delete(save);
    }

    @Test
    void removeTrigger() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        triggerQueue.receive(trigger -> {
            assertThat(trigger, is(nullValue()));
            countDownLatch.countDown();
        });

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

        Flow save = flowRepository.create(flow);

        assertThat(flowRepository.findById(flow.getNamespace(), flow.getId()).isPresent(), is(true));

        Flow update = Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();
        ;

        Flow updated = flowRepository.update(update, flow);

        countDownLatch.await(15, TimeUnit.SECONDS);

        assertThat(updated.getTriggers(), is(nullValue()));

        flowRepository.delete(updated);

        assertThat(FlowListener.getEmits().size(), is(3));
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.CREATE).count(), is(1L));
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.UPDATE).count(), is(1L));
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.DELETE).count(), is(1L));
    }


    @Test
    void removeTriggerDelete() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        triggerQueue.receive(trigger -> {
            assertThat(trigger, is(nullValue()));
            countDownLatch.countDown();
        });

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

        Flow save = flowRepository.create(flow);

        assertThat(flowRepository.findById(flow.getNamespace(), flow.getId()).isPresent(), is(true));

        flowRepository.delete(save);
        countDownLatch.await(15, TimeUnit.SECONDS);

        assertThat(FlowListener.getEmits().size(), is(2));
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.CREATE).count(), is(1L));
        assertThat(FlowListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.DELETE).count(), is(1L));
    }

    @Test
    void findDistinctNamespace() {
        List<String> distinctNamespace = flowRepository.findDistinctNamespace();
        assertThat((long) distinctNamespace.size(), is(2L));
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
