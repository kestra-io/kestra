package org.kestra.core.repositories;

import com.devskiller.friendly_id.FriendlyId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import io.micronaut.test.annotation.MicronautTest;
import org.kestra.core.Helpers;
import org.kestra.core.serializers.JacksonMapper;
import org.kestra.core.tasks.scripts.Bash;
import org.kestra.core.utils.TestsUtils;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.Input;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
public abstract class AbstractFlowRepositoryTest {
    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    private void init() throws IOException, URISyntaxException {
        TestsUtils.loads(repositoryLoader);
    }

    private static Flow.FlowBuilder builder() {
        return Flow.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace("org.kestra.unittest");
    }

    @Test
    void findById() {
        Flow flow = builder()
            .revision(3)
            .build();
        flowRepository.save(flow);

        Optional<Flow> full = flowRepository.findById(flow.getNamespace(), flow.getId());
        assertThat(full.isPresent(), is(true));

        full.ifPresent(current -> {
            assertThat(full.get().getRevision(), is(3));
        });
    }

    @Test
    void revision() throws JsonProcessingException {
        Flow flow = flowRepository.save(Flow.builder()
            .id("AbstractFlowRepositoryTest")
            .namespace("org.kestra.unittest")
            .inputs(ImmutableList.of(Input.builder().type(Input.Type.STRING).name("a").build()))
            .build());

        Flow notSaved = flowRepository.save(flow);

        assertThat(flow, is(notSaved));

        Flow flowRev2 = Flow.builder()
            .id("AbstractFlowRepositoryTest")
            .namespace("org.kestra.unittest")
            .tasks(Collections.singletonList(
                Bash.builder()
                    .type(Bash.class.getName())
                    .commands(Collections.singletonList("echo 1").toArray(new String[0]))
                    .build()
            ))
            .inputs(ImmutableList.of(Input.builder().type(Input.Type.STRING).name("b").build()))
            .build();

        Flow incremented = flowRepository.save(flowRev2);
        assertThat(incremented.getRevision(), is(2));


        List<Flow> revisions = flowRepository.findRevisions(flow.getNamespace(), flow.getId());
        assertThat(revisions.size(), is(2));

        Flow incremented2 = flowRepository.save(JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(flowRev2), Flow.class));
        assertThat(incremented2.getRevision(), is(2));

        flowRepository.delete(flow);
        flowRepository.delete(incremented);
    }

    @Test
    void save() {
        Flow flow = builder().revision(12).build();
        Flow save = flowRepository.save(flow);

        assertThat(save.getRevision(), is(12));
    }

    @Test
    void saveNoRevision() {
        Flow flow = builder().build();
        Flow save = flowRepository.save(flow);

        assertThat(save.getRevision(), is(1));

        flowRepository.delete(save);
    }

    @Test
    void findAll() {
        List<Flow> save = flowRepository.findAll();

        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT));
    }

    @Test
    void delete() {
        Flow flow = builder().build();

        Flow save = flowRepository.save(flow);
        flowRepository.delete(save);

        assertThat(flowRepository.findById(flow.getNamespace(), flow.getId()).isPresent(), is(false));
    }
}