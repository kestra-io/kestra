package io.kestra.core.reporter.reports;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.Label;
import io.kestra.core.models.assets.AssetsDeclaration;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.Output;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.check.Check;
import io.kestra.core.models.flows.input.FormInput;
import io.kestra.core.models.flows.input.IntInput;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.flows.quota.Quota;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.types.MaxDurationSLA;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Cache;
import io.kestra.core.models.tasks.WorkerSelector;
import io.kestra.core.models.tasks.retrys.Constant;
import io.kestra.core.reporter.Reportable;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.flow.Sequential;
import io.kestra.plugin.core.log.Log;
import io.kestra.plugin.core.trigger.Schedule;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public abstract class AbstractFeatureUsageReportTest {

    @Inject
    FeatureUsageReport featureUsageReport;

    @Inject
    FlowRepositoryInterface flowRepository;

    @Test
    public void shouldGetReport() {
        // When
        Instant now = Instant.now();
        FeatureUsageReport.UsageEvent event = featureUsageReport.report(
            now,
            Reportable.TimeInterval.of(now.minus(Duration.ofDays(1)).atZone(ZoneId.systemDefault()), now.atZone(ZoneId.systemDefault()))
        );

        // Then
        assertThat(event.getExecutions().getDailyExecutionsCount().size()).isGreaterThan(0);
    }

    @Test
    public void shouldReportFlowFeatureUsage() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String namespace = "io.kestra.unittest." + IdUtils.create();
        FeatureUsageReport.UsageEvent before = report();

        FlowWithSource flowWithoutFeatures = FlowWithSource.builder()
            .tenantId(tenant)
            .id(IdUtils.create())
            .namespace(namespace)
            .tasks(List.of(Log.builder().id("main").type(Log.class.getName()).message("hello").build()))
            .build();

        FlowWithSource flowWithFeatures = FlowWithSource.builder()
            .tenantId(tenant)
            .id(IdUtils.create())
            .namespace(namespace)
            .labels(List.of(new Label("key", "value")))
            .inputs(
                List.of(
                    StringInput.builder().id("in").type(Type.STRING).build(),
                    FormInput.builder().id("form").type(Type.FORM).inputs(
                        List.of(
                            IntInput.builder().id("count").type(Type.INT).build()
                        )
                    ).build()
                )
            )
            .outputs(List.of(Output.builder().id("out").type(Type.STRING).value("value").build()))
            .variables(Map.of("var", "value"))
            .workerSelector(new WorkerSelector(List.of("tag"), null))
            .concurrency(Concurrency.builder().limit(2).behavior(Concurrency.Behavior.CANCEL).build())
            .retry(Constant.builder().interval(Duration.ofSeconds(5)).maxAttempts(3).build())
            .sla(
                List.of(
                    MaxDurationSLA.builder()
                        .id("sla1")
                        .type(SLA.Type.MAX_DURATION)
                        .behavior(SLA.Behavior.FAIL)
                        .duration(Duration.ofMinutes(10))
                        .build()
                )
            )
            .checks(
                List.of(
                    Check.builder().when("{{ true }}").message("ok").behavior(Check.Behavior.FAIL_EXECUTION).build()
                )
            )
            .quotas(
                List.of(
                    Quota.builder().duration(Duration.ofMinutes(5)).limit(10L).behavior(Quota.Behavior.FAIL).build()
                )
            )
            .tasks(
                List.of(
                    Log.builder()
                        .id("main")
                        .type(Log.class.getName())
                        .message("hello")
                        .retry(Constant.builder().interval(Duration.ofSeconds(1)).build())
                        .timeout(Property.ofValue(Duration.ofSeconds(30)))
                        .workerSelector(new WorkerSelector(List.of("tag"), null))
                        .allowFailure(true)
                        .logToFile(true)
                        .runIf("{{ 1 == 1 }}")
                        .allowWarning(true)
                        .taskCache(new Cache(true, Duration.ofMinutes(1)))
                        .assets(new AssetsDeclaration(null, null, null))
                        .build(),
                    Sequential.builder()
                        .id("seq")
                        .type(Sequential.class.getName())
                        .tasks(List.of(Log.builder().id("inner").type(Log.class.getName()).message("inner").build()))
                        .errors(List.of(Log.builder().id("innerErr").type(Log.class.getName()).message("err").build()))
                        ._finally(List.of(Log.builder().id("innerFin").type(Log.class.getName()).message("fin").build()))
                        .build()
                )
            )
            .errors(List.of(Log.builder().id("err").type(Log.class.getName()).message("error").build()))
            ._finally(List.of(Log.builder().id("fin").type(Log.class.getName()).message("finally").build()))
            .afterExecution(List.of(Log.builder().id("after").type(Log.class.getName()).message("after").build()))
            .triggers(
                List.of(
                    Schedule.builder()
                        .id("schedule")
                        .type(Schedule.class.getName())
                        .cron("0 1 9 * * *")
                        .workerSelector(new WorkerSelector(List.of("tag"), null))
                        .labels(List.of(new Label("trig", "label")))
                        .stopAfter(List.of(State.Type.FAILED))
                        .logToFile(true)
                        .failOnTriggerError(true)
                        .allowConcurrent(true)
                        .assets(new AssetsDeclaration(null, null, null))
                        .build()
                )
            )
            .build();

        FlowWithSource createdWithoutFeatures = null;
        FlowWithSource createdWithFeatures = null;
        try {
            createdWithoutFeatures = flowRepository.create(GenericFlow.of(flowWithoutFeatures));
            createdWithFeatures = flowRepository.create(GenericFlow.of(flowWithFeatures));

            // When
            FeatureUsageReport.UsageEvent after = report();

            // Then
            assertThat(after.getFlows().getCount()).isEqualTo(before.getFlows().getCount() + 2);
            assertThat(after.getFlows().getNamespacesCount()).isEqualTo(before.getFlows().getNamespacesCount() + 1);
            assertThat(after.getFlows().getHasInputsCount()).isEqualTo(before.getFlows().getHasInputsCount() + 1);
            assertThat(after.getFlows().getHasOutputsCount()).isEqualTo(before.getFlows().getHasOutputsCount() + 1);
            assertThat(after.getFlows().getHasLabelsCount()).isEqualTo(before.getFlows().getHasLabelsCount() + 1);
            assertThat(after.getFlows().getHasVariablesCount()).isEqualTo(before.getFlows().getHasVariablesCount() + 1);
            assertThat(after.getFlows().getHasWorkerSelectorCount()).isEqualTo(before.getFlows().getHasWorkerSelectorCount() + 1);
            assertThat(after.getFlows().getHasErrorsCount()).isEqualTo(before.getFlows().getHasErrorsCount() + 1);
            assertThat(after.getFlows().getHasFinallyCount()).isEqualTo(before.getFlows().getHasFinallyCount() + 1);
            assertThat(after.getFlows().getHasAfterExecutionCount()).isEqualTo(before.getFlows().getHasAfterExecutionCount() + 1);
            assertThat(after.getFlows().getHasTriggersCount()).isEqualTo(before.getFlows().getHasTriggersCount() + 1);
            assertThat(after.getFlows().getHasConcurrencyCount()).isEqualTo(before.getFlows().getHasConcurrencyCount() + 1);
            assertThat(after.getFlows().getHasRetryCount()).isEqualTo(before.getFlows().getHasRetryCount() + 1);
            assertThat(after.getFlows().getHasSlaCount()).isEqualTo(before.getFlows().getHasSlaCount() + 1);
            assertThat(after.getFlows().getHasChecksCount()).isEqualTo(before.getFlows().getHasChecksCount() + 1);
            assertThat(after.getFlows().getHasQuotasCount()).isEqualTo(before.getFlows().getHasQuotasCount() + 1);
            assertThat(after.getFlows().getInputTypeCount().getOrDefault("STRING", 0L)).isEqualTo(before.getFlows().getInputTypeCount().getOrDefault("STRING", 0L) + 1);
            assertThat(after.getFlows().getInputTypeCount().getOrDefault("FORM", 0L)).isEqualTo(before.getFlows().getInputTypeCount().getOrDefault("FORM", 0L) + 1);
            assertThat(after.getFlows().getInputTypeCount().getOrDefault("INT", 0L)).isEqualTo(before.getFlows().getInputTypeCount().getOrDefault("INT", 0L) + 1);

            assertThat(after.getFlows().getTasks().getHasRetryCount()).isEqualTo(before.getFlows().getTasks().getHasRetryCount() + 1);
            assertThat(after.getFlows().getTasks().getHasTimeoutCount()).isEqualTo(before.getFlows().getTasks().getHasTimeoutCount() + 1);
            assertThat(after.getFlows().getTasks().getHasWorkerSelectorCount()).isEqualTo(before.getFlows().getTasks().getHasWorkerSelectorCount() + 1);
            assertThat(after.getFlows().getTasks().getHasAllowFailureCount()).isEqualTo(before.getFlows().getTasks().getHasAllowFailureCount() + 1);
            assertThat(after.getFlows().getTasks().getHasLogToFileCount()).isEqualTo(before.getFlows().getTasks().getHasLogToFileCount() + 1);
            assertThat(after.getFlows().getTasks().getHasRunIfCount()).isEqualTo(before.getFlows().getTasks().getHasRunIfCount() + 1);
            assertThat(after.getFlows().getTasks().getHasAllowWarningCount()).isEqualTo(before.getFlows().getTasks().getHasAllowWarningCount() + 1);
            assertThat(after.getFlows().getTasks().getHasCacheCount()).isEqualTo(before.getFlows().getTasks().getHasCacheCount() + 1);
            assertThat(after.getFlows().getTasks().getHasAssetsCount()).isEqualTo(before.getFlows().getTasks().getHasAssetsCount() + 1);
            assertThat(after.getFlows().getTasks().getHasErrorsCount()).isEqualTo(before.getFlows().getTasks().getHasErrorsCount() + 1);
            assertThat(after.getFlows().getTasks().getHasFinallyCount()).isEqualTo(before.getFlows().getTasks().getHasFinallyCount() + 1);

            assertThat(after.getFlows().getTriggers().getHasWhenCount()).isEqualTo(before.getFlows().getTriggers().getHasWhenCount() + 1);
            assertThat(after.getFlows().getTriggers().getHasWorkerSelectorCount()).isEqualTo(before.getFlows().getTriggers().getHasWorkerSelectorCount() + 1);
            assertThat(after.getFlows().getTriggers().getHasLabelsCount()).isEqualTo(before.getFlows().getTriggers().getHasLabelsCount() + 1);
            assertThat(after.getFlows().getTriggers().getHasStopAfterCount()).isEqualTo(before.getFlows().getTriggers().getHasStopAfterCount() + 1);
            assertThat(after.getFlows().getTriggers().getHasLogToFileCount()).isEqualTo(before.getFlows().getTriggers().getHasLogToFileCount() + 1);
            assertThat(after.getFlows().getTriggers().getHasFailOnErrorCount()).isEqualTo(before.getFlows().getTriggers().getHasFailOnErrorCount() + 1);
            assertThat(after.getFlows().getTriggers().getHasAllowConcurrentCount()).isEqualTo(before.getFlows().getTriggers().getHasAllowConcurrentCount() + 1);
            assertThat(after.getFlows().getTriggers().getHasAssetsCount()).isEqualTo(before.getFlows().getTriggers().getHasAssetsCount() + 1);
        } finally {
            if (createdWithoutFeatures != null) {
                flowRepository.delete(createdWithoutFeatures);
            }
            if (createdWithFeatures != null) {
                flowRepository.delete(createdWithFeatures);
            }
        }
    }

    private FeatureUsageReport.UsageEvent report() {
        Instant now = Instant.now();
        return featureUsageReport.report(
            now,
            Reportable.TimeInterval.of(now.minus(Duration.ofDays(1)).atZone(ZoneId.systemDefault()), now.atZone(ZoneId.systemDefault()))
        );
    }
}
