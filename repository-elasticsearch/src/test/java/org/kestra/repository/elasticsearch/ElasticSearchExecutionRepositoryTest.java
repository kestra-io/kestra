package org.kestra.repository.elasticsearch;

import com.devskiller.friendly_id.FriendlyId;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.repositories.ExecutionRepositoryInterface;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class ElasticSearchExecutionRepositoryTest {
    public static final String NAMESPACE = "org.kestra.unittest";
    public static final String FLOW = "full";

    @Inject
    ExecutionRepositoryInterface executionRepository;

    @Inject
    ElasticSearchRepositoryTestUtils utils;

    private static Execution.ExecutionBuilder builder() {
        return Execution.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace(NAMESPACE)
            .flowId(FLOW)
            .flowRevision(1)
            .state(new State());
    }

    @Test
    void findById() {
        executionRepository.save(ExecutionFixture.EXECUTION_1);

        Optional<Execution> full = executionRepository.findById(ExecutionFixture.EXECUTION_1.getId());
        assertThat(full.isPresent(), is(true));

        full.ifPresent(current -> {
            assertThat(full.get().getId(), is(ExecutionFixture.EXECUTION_1.getId()));
        });
    }

    @Test
    void mappingConflict() {
        executionRepository.save(ExecutionFixture.EXECUTION_2);
        executionRepository.save(ExecutionFixture.EXECUTION_1);

        ArrayListTotal<Execution> page1 = executionRepository.findByFlowId(NAMESPACE, FLOW, Pageable.from(1, 10));

        assertThat(page1.size(), is(2));
    }

    @Test
    void findByFlowId() {
        for (int i = 0; i < 28; i++) {
            executionRepository.save(builder().build());
        }

        ArrayListTotal<Execution> page1 = executionRepository.findByFlowId(NAMESPACE, FLOW, Pageable.from(1, 10));
        assertThat(page1.size(), is(10));
        assertThat(page1.getTotal(), is(28L));

        ArrayListTotal<Execution> page2 = executionRepository.findByFlowId(NAMESPACE, FLOW, Pageable.from(2, 10));
        assertThat(page2.size(), is(10));

        ArrayListTotal<Execution> page3 = executionRepository.findByFlowId(NAMESPACE, FLOW, Pageable.from(3, 10));
        assertThat(page3.size(), is(8));
    }

    @AfterEach
    protected void tearDown() throws IOException {
        utils.tearDown();
    }
}
