package io.kestra.repository.h2;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.utils.IdUtils;

import io.micrometer.core.instrument.Timer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class H2JdbcQueryMetricsTest {
    @Inject
    private SettingRepositoryInterface settingRepository;

    @Inject
    private MetricRegistry metricRegistry;

    @Test
    void shouldTagQueryDurationWithBoundedTypeAndTableWhenRepositoryQueryRuns() {
        // Given
        Setting setting = Setting.builder().key(IdUtils.create()).value("value").build();
        settingRepository.save(setting);

        // When
        settingRepository.findByKey(setting.getKey());

        // Then
        // this exercises the real wiring end-to-end: JooqExecuteListenerFactory -> JdbcQueryTags ->
        // MetricRegistry, so a regression in any of them (e.g. the tag deriving the wrong table,
        // or a newline sneaking back into "type") shows up here, not just in the unit test
        List<Timer> timers = metricRegistry.find(MetricRegistry.METRIC_JDBC_QUERY_DURATION).timers().stream().toList();
        assertThat(timers).isNotEmpty();
        assertThat(timers)
            .extracting(timer -> timer.getId().getTag("table"))
            .contains("settings");
        assertThat(timers)
            .extracting(timer -> timer.getId().getTag("type"))
            .allSatisfy(type -> assertThat(type).doesNotContain("\n"));
    }
}
