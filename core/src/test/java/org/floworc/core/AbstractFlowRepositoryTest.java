package org.floworc.core;

import com.devskiller.friendly_id.FriendlyId;
import io.micronaut.test.annotation.MicronautTest;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.core.repositories.LocalFlowRepositoryLoader;
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
            .namespace("org.floworc.unittest");
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
        Flow flow = builder().build();
        flowRepository.save(flow);
        flowRepository.save(flow);

        flowRepository.findById(flow.getNamespace(), flow.getId()).ifPresent(current -> {
            Flow save = flowRepository.save(current.withRevision(null));
            assertThat(save.getRevision(), is(3));
        });

        List<Flow> revisions = flowRepository.findRevisions(flow.getNamespace(), flow.getId());
        assertThat(revisions.size(), is(3));
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

        assertThat(save.size(), is(14));
    }

    @Test
    void delete() {
        Flow flow = builder().build();

        Flow save = flowRepository.save(flow);
        flowRepository.delete(save);

        assertThat(flowRepository.findById(flow.getNamespace(), flow.getId()).isPresent(), is(false));
    }
}