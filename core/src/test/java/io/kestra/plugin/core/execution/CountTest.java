package io.kestra.plugin.core.execution;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.AbstractExecutionRepositoryTest;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class CountTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    ExecutionRepositoryInterface executionRepository;


    @Test
    void run() throws Exception {
        for (int i = 0; i < 28; i++) {
            executionRepository.save(AbstractExecutionRepositoryTest.builder(
                i < 5 ? State.Type.RUNNING : (i < 8 ? State.Type.FAILED : State.Type.SUCCESS),
                i < 4 ? "first" : (i < 10 ? "second" : "third")
            ).build());
        }
        // matching one
        Count task = Count.builder()
            .id(IdUtils.create())
            .type(Count.class.getName())
            .flows(List.of(
                new Flow(AbstractExecutionRepositoryTest.NAMESPACE, "first"),
                new Flow(AbstractExecutionRepositoryTest.NAMESPACE, "second"),
                new Flow(AbstractExecutionRepositoryTest.NAMESPACE, "third")
            ))
            .expression("{{ count >= 5 }}")
            .startDate(new Property<>("{{ now() | dateAdd (-30, 'DAYS') }}"))
            .endDate(new Property<>("{{ now() }}"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of("namespace", "io.kestra.unittest"));
        Count.Output run = task.run(runContext);

        assertThat(run.getResults().size(), is(2));
        assertThat(run.getResults().stream().filter(f -> f.getFlowId().equals("second")).count(), is(1L));
        assertThat(run.getResults().stream().filter(f -> f.getFlowId().equals("second")).findFirst().get().getCount(), is(6L));
        assertThat(run.getResults().stream().filter(f -> f.getFlowId().equals("third")).count(), is(1L));
        assertThat(run.getResults().stream().filter(f -> f.getFlowId().equals("third")).findFirst().get().getCount(), is(18L));
        assertThat(run.getTotal(), is(24L));

        // add state filter no result
        run = Count.builder()
            .flows(List.of(
                new Flow(AbstractExecutionRepositoryTest.NAMESPACE, "first"),
                new Flow(AbstractExecutionRepositoryTest.NAMESPACE, "second"),
                new Flow(AbstractExecutionRepositoryTest.NAMESPACE, "third")
            ))
            .states(Property.of(List.of(State.Type.RUNNING)))
            .expression("{{ count >= 5 }}")
            .build()
            .run(runContext);

        assertThat(run.getResults().size(), is(0));

        // non-matching entry
        run = Count.builder()
            .flows(List.of(
                new Flow("io.kestra.test", "missing"),
                new Flow(AbstractExecutionRepositoryTest.NAMESPACE, "second"),
                new Flow(AbstractExecutionRepositoryTest.NAMESPACE, "third")
            ))
            .expression("{{ count == 0 }}")
            .build()
            .run(runContext);

        assertThat(run.getResults().size(), is(1));
        assertThat(run.getResults().stream().filter(f -> f.getFlowId().equals("missing")).count(), is(1L));
        assertThat(run.getTotal(), is(0L));

    }
}