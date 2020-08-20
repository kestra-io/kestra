package org.kestra.core.repositories;

import com.devskiller.friendly_id.FriendlyId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kestra.core.Helpers;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.Input;
import org.kestra.core.serializers.JacksonMapper;
import org.kestra.core.tasks.debugs.Return;
import org.kestra.core.tasks.scripts.Bash;
import org.kestra.core.utils.TestsUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.validation.ConstraintViolationException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
public abstract class AbstractFlowRepositoryTest {
    @Inject
    protected FlowRepositoryInterface flowRepository;

    @Inject
    private LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    private void init() throws IOException, URISyntaxException {
        TestsUtils.loads(repositoryLoader);
    }

    private static Flow.FlowBuilder builder() {
        return Flow.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace("org.kestra.unittest")
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()));
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
            assertThat(full.get().getRevision(), is(3));
        });
    }

    @Test
    protected void revision() throws JsonProcessingException {
        // create
        Flow flow = flowRepository.create(Flow.builder()
            .id("AbstractFlowRepositoryTest")
            .namespace("org.kestra.unittest")
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .inputs(ImmutableList.of(Input.builder().type(Input.Type.STRING).name("a").build()))
            .build());

        // submit new one, no change
        Flow notSaved = flowRepository.update(flow, flow);
        assertThat(flow.getRevision(), is(notSaved.getRevision()));

        // submit new one with change
        Flow flowRev2 = Flow.builder()
            .id("AbstractFlowRepositoryTest")
            .namespace("org.kestra.unittest")
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

        // cleanup
        flowRepository.delete(flow);
        flowRepository.delete(incremented);

        // revisions is still findable after delete
        revisions = flowRepository.findRevisions(flow.getNamespace(), flow.getId());
        assertThat(revisions.size(), is(3));

        Optional<Flow> findDeleted = flowRepository.findById(
            flow.getNamespace(),
            flow.getId(),
            Optional.of(flow.getRevision())
        );
        assertThat(findDeleted.isPresent(), is(true));
        assertThat(findDeleted.get().getRevision(), is(flow.getRevision()));
    }

    @Test
    void save() {
        Flow flow = builder().revision(12).build();
        Flow save = flowRepository.create(flow);

        assertThat(save.getRevision(), is(12));
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
    void findByNamespace() {
        List<Flow> save = flowRepository.findByNamespace("org.kestra.tests");
        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT - 1));

        save = flowRepository.findByNamespace("org.kestra.tests.minimal.bis");
        assertThat((long) save.size(), is(1L));
    }

    @Test
    void delete() {
        Flow flow = builder().build();

        Flow save = flowRepository.create(flow);
        flowRepository.delete(save);

        assertThat(flowRepository.findById(flow.getNamespace(), flow.getId()).isPresent(), is(false));
    }


    @Test
    void updateConflict() {
        String flowId = FriendlyId.createFriendlyId();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace("org.kestra.unittest")
            .inputs(ImmutableList.of(Input.builder().type(Input.Type.STRING).name("a").build()))
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();

        Flow save = flowRepository.create(flow);

        assertThat(flowRepository.findById(flow.getNamespace(), flow.getId()).isPresent(), is(true));

        Flow update = Flow.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace("org.kestra.unittest2")
            .inputs(ImmutableList.of(Input.builder().type(Input.Type.STRING).name("b").build()))
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();;

        ConstraintViolationException e = assertThrows(
            ConstraintViolationException.class,
            () -> flowRepository.update(update, flow)
        );

        assertThat(e.getConstraintViolations().size(), is(2));

        flowRepository.delete(save);

    }
}
