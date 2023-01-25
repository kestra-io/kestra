package io.kestra.repository.h2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.tasks.scripts.Bash;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepositoryTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class H2lFlowRepositoryTest extends AbstractJdbcFlowRepositoryTest {
    @Inject
    protected FlowRepositoryInterface flowRepository;


    @Test
    protected void revision() throws JsonProcessingException {
        String flowId = IdUtils.create();
        // create with builder
        Flow flow = Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .inputs(ImmutableList.of(Input.builder().type(Input.Type.STRING).name("a").build()))
            .build();
        // create with repository
        flow = flowRepository.create(flow, JacksonMapper.ofYaml().writeValueAsString(flow)).getFlow();

        // submit new one, no change
        Flow notSaved = flowRepository.update(flow, flow, JacksonMapper.ofYaml().writeValueAsString(flow)).getFlow();
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
        Flow incremented = flowRepository.update(flowRev2, flow, JacksonMapper.ofYaml().writeValueAsString(flowRev2)).getFlow();
        assertThat(incremented.getRevision(), is(2));

        // revision is well saved
        List<Flow> revisions = flowRepository.findRevisions(flow.getNamespace(), flow.getId());
        assertThat(revisions.size(), is(2));

        // submit the same one serialized, no changed
        Flow incremented2 = flowRepository.update(
            JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(flowRev2), Flow.class),
            flowRev2,
            JacksonMapper.ofYaml().writeValueAsString(JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(flowRev2), Flow.class))
        ).getFlow();
        assertThat(incremented2.getRevision(), is(2));

        // resubmit first one, revision is incremented
        Flow incremented3 = flowRepository.update(
            JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(flow), Flow.class),
            flowRev2,
            JacksonMapper.ofYaml().writeValueAsString(JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(flow), Flow.class))
        ).getFlow();
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
        Flow incremented4 = flowRepository.create(flow, JacksonMapper.ofYaml().writeValueAsString(flow)).getFlow();

        assertThat(incremented4.getRevision(), is(5));
    }

}
