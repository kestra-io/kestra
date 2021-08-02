package io.kestra.core.tasks.executions;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.repository.elasticsearch.ElasticSearchExecutionRepository;
import io.kestra.repository.elasticsearch.ElasticSearchExecutionRepositoryTest;
import io.kestra.repository.elasticsearch.ElasticSearchRepositoryTestUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class CountsTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    ElasticSearchExecutionRepository executionRepository;

    @Inject
    ElasticSearchRepositoryTestUtils utils;

    @Test
    void run() throws Exception {
        for (int i = 0; i < 28; i++) {
            executionRepository.save(ElasticSearchExecutionRepositoryTest.builder(
                i < 5 ? State.Type.RUNNING : (i < 8 ? State.Type.FAILED : State.Type.SUCCESS),
                i < 4 ? "first" : (i < 10 ? "second" : "third")
            ).build());
        }

        RunContext runContext = runContextFactory.of(ImmutableMap.of("namespace", "io.kestra.unittest"));

        // matching one
        Counts.Output run = Counts.builder()
            .flows(List.of(
                new Flow(ElasticSearchExecutionRepositoryTest.NAMESPACE, "first"),
                new Flow(ElasticSearchExecutionRepositoryTest.NAMESPACE, "second"),
                new Flow(ElasticSearchExecutionRepositoryTest.NAMESPACE, "third")
            ))
            .expression("{{ gte count 5 }}")
            .startDate("{{ dateAdd (now) -30 'DAYS' }}")
            .endDate("{{ now }}")
            .build()
            .run(runContext);

        assertThat(run.getResults().size(), is(2));
        assertThat(run.getResults().stream().filter(f -> f.getFlowId().equals("second")).count(), is(1L));
        assertThat(run.getResults().stream().filter(f -> f.getFlowId().equals("third")).count(), is(1L));

        // add state filter no result
        run = Counts.builder()
            .flows(List.of(
                new Flow(ElasticSearchExecutionRepositoryTest.NAMESPACE, "first"),
                new Flow(ElasticSearchExecutionRepositoryTest.NAMESPACE, "second"),
                new Flow(ElasticSearchExecutionRepositoryTest.NAMESPACE, "third")
            ))
            .states(List.of(State.Type.RUNNING))
            .expression("{{ gte count 5 }}")
            .build()
            .run(runContext);

        assertThat(run.getResults().size(), is(0));

        // non matching entry
        run = Counts.builder()
            .flows(List.of(
                new Flow("io.kestra.test", "missing"),
                new Flow(ElasticSearchExecutionRepositoryTest.NAMESPACE, "second"),
                new Flow(ElasticSearchExecutionRepositoryTest.NAMESPACE, "third")
            ))
            .expression("{{ eq count 0 }}")
            .build()
            .run(runContext);

        assertThat(run.getResults().size(), is(1));
        assertThat(run.getResults().stream().filter(f -> f.getFlowId().equals("missing")).count(), is(1L));
    }

    @AfterEach
    protected void tearDown() throws IOException {
        utils.tearDown();
        executionRepository.initMapping();
    }
}