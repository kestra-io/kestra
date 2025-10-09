package io.kestra.plugin.core.execution;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.AbstractExecutionRepositoryTest;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class CountTest {

    public static final String NAMESPACE = "io.kestra.unittest";
    @Inject
    TestRunContextFactory runContextFactory;

    @Inject
    ExecutionRepositoryInterface executionRepository;


    @Test
    void run() throws Exception {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        for (int i = 0; i < 28; i++) {
            executionRepository.save(AbstractExecutionRepositoryTest.builder(
                tenant,
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
            .startDate(Property.ofExpression("{{ now() | dateAdd (-30, 'DAYS') }}"))
            .endDate(Property.ofExpression("{{ now() }}"))
            .build();

        RunContext runContext = runContextFactory.of("id", NAMESPACE, tenant);

        Count.Output run = task.run(runContext);

        assertThat(run.getResults().size()).isEqualTo(2);
        assertThat(run.getResults().stream().filter(f -> f.getFlowId().equals("second")).count()).isEqualTo(1L);
        assertThat(run.getResults().stream().filter(f -> f.getFlowId().equals("second")).findFirst().get().getCount()).isEqualTo(6L);
        assertThat(run.getResults().stream().filter(f -> f.getFlowId().equals("third")).count()).isEqualTo(1L);
        assertThat(run.getResults().stream().filter(f -> f.getFlowId().equals("third")).findFirst().get().getCount()).isEqualTo(18L);
        assertThat(run.getTotal()).isEqualTo(24L);

        // add state filter no result
        run = Count.builder()
            .flows(List.of(
                new Flow(AbstractExecutionRepositoryTest.NAMESPACE, "first"),
                new Flow(AbstractExecutionRepositoryTest.NAMESPACE, "second"),
                new Flow(AbstractExecutionRepositoryTest.NAMESPACE, "third")
            ))
            .states(Property.ofValue(List.of(State.Type.RUNNING)))
            .expression("{{ count >= 5 }}")
            .build()
            .run(runContext);

        assertThat(run.getResults().size()).isZero();

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

        assertThat(run.getResults().size()).isEqualTo(1);
        assertThat(run.getResults().stream().filter(f -> f.getFlowId().equals("missing")).count()).isEqualTo(1L);
        assertThat(run.getTotal()).isEqualTo(0L);

    }
}