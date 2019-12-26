package org.kestra.core.repositories;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.collect.ImmutableList;
import io.micronaut.test.annotation.MicronautTest;
import org.kestra.core.Utils;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.Input;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.repositories.LocalFlowRepositoryLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
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
        Utils.loads(repositoryLoader);
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
    void revision() {
        Flow flow = flowRepository.save(Flow.builder()
            .id("AbstractFlowRepositoryTest")
            .namespace("org.kestra.unittest")
            .inputs(ImmutableList.of(Input.builder().type(Input.Type.STRING).name("a").build()))
            .build());

        Flow notSaved = flowRepository.save(flow);

        assertThat(flow, is(notSaved));

        Flow incremented = flowRepository.save(Flow.builder()
            .id("AbstractFlowRepositoryTest")
            .namespace("org.kestra.unittest")
            .inputs(ImmutableList.of(Input.builder().type(Input.Type.STRING).name("b").build()))
            .build()
        );
        assertThat(incremented.getRevision(), is(2));


        List<Flow> revisions = flowRepository.findRevisions(flow.getNamespace(), flow.getId());
        assertThat(revisions.size(), is(2));

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

        assertThat(save.size(), is(17));
    }

    @Test
    void delete() {
        Flow flow = builder().build();

        Flow save = flowRepository.save(flow);
        flowRepository.delete(save);

        assertThat(flowRepository.findById(flow.getNamespace(), flow.getId()).isPresent(), is(false));
    }
}