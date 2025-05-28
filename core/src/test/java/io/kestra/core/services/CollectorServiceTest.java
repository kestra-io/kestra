package io.kestra.core.services;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.Helpers;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.ServerType;
import io.kestra.core.models.Setting;
import io.kestra.core.models.collectors.Usage;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.plugin.core.http.Trigger;
import io.kestra.plugin.core.log.Log;
import io.kestra.plugin.core.trigger.Schedule;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class CollectorServiceTest {

    @Test
    public void metrics() throws URISyntaxException {
        ImmutableMap<String, Object> properties = ImmutableMap.of("kestra.server-type", ServerType.STANDALONE.name());

        try (ApplicationContext applicationContext = Helpers.applicationContext(properties).start()) {
            MetricRegistry metricRegistry = applicationContext.getBean(MetricRegistry.class);
            // inject fake metrics to have plugin metrics
            metricRegistry.timer(MetricRegistry.METRIC_WORKER_ENDED_DURATION, MetricRegistry.METRIC_WORKER_ENDED_DURATION_DESCRIPTION, MetricRegistry.TAG_TASK_TYPE, Log.class.getName())
                .record(() -> Duration.ofSeconds(1));
            metricRegistry.timer(MetricRegistry.METRIC_WORKER_TRIGGER_DURATION, MetricRegistry.METRIC_WORKER_TRIGGER_DURATION_DESCRIPTION, MetricRegistry.TAG_TRIGGER_TYPE, Trigger.class.getName())
                .record(() -> Duration.ofSeconds(1));
            metricRegistry.timer(MetricRegistry.METRIC_SCHEDULER_TRIGGER_EVALUATION_DURATION, MetricRegistry.METRIC_SCHEDULER_TRIGGER_EVALUATION_DURATION_DESCRIPTION, MetricRegistry.TAG_TRIGGER_TYPE, Schedule.class.getName())
                .record(() -> Duration.ofSeconds(1));

            CollectorService collectorService = applicationContext.getBean(CollectorService.class);
            Usage metrics = collectorService.metrics(true);

            assertThat(metrics.getUri()).isEqualTo("https://mysuperhost.com/subpath");

            assertThat(metrics.getUuid()).isNotNull();
            assertThat(metrics.getVersion()).isNotNull();
            assertThat(metrics.getStartTime()).isNotNull();
            assertThat(metrics.getEnvironments()).contains("test");
            assertThat(metrics.getStartTime()).isNotNull();
            assertThat(metrics.getHost().getUuid()).isNotNull();
            assertThat(metrics.getHost().getHardware().getLogicalProcessorCount()).isNotNull();
            assertThat(metrics.getHost().getJvm().getName()).isNotNull();
            assertThat(metrics.getHost().getOs().getFamily()).isNotNull();
            assertThat(metrics.getConfigurations().getRepositoryType()).isEqualTo("memory");
            assertThat(metrics.getConfigurations().getQueueType()).isEqualTo("memory");
            assertThat(metrics.getExecutions()).isNotNull();
            // 1 per hour
            assertThat(metrics.getExecutions().getDailyExecutionsCount().size()).isGreaterThan(0);
            // no task runs as it's an empty instance
            assertThat(metrics.getExecutions().getDailyTaskRunsCount()).isNull();
            assertThat(metrics.getInstanceUuid()).isEqualTo(TestSettingRepository.instanceUuid);
            // we have 3 metrics so we should have the info for the related plugins
            assertThat(metrics.getPluginMetrics()).hasSize(3);
        }
    }

    @Singleton
    @Requires(property = "kestra.unittest")
    @Primary
    public static class TestSettingRepository implements SettingRepositoryInterface {
        public static Object instanceUuid = null;

        @Override
        public Optional<Setting> findByKey(String key) {
            return Optional.empty();
        }

        @Override
        public List<Setting> findAll() {
            return new ArrayList<>();
        }

        @Override
        public Setting save(Setting setting) throws ConstraintViolationException {
            if (setting.getKey().equals(Setting.INSTANCE_UUID)) {
                TestSettingRepository.instanceUuid = setting.getValue();
            }

            return setting;
        }

        @Override
        public Setting delete(Setting setting) {
            return setting;
        }
    }
}