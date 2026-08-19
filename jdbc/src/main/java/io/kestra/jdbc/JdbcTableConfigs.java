package io.kestra.jdbc;

import java.util.List;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import jakarta.inject.Inject;
import lombok.Getter;

@ConfigurationProperties("kestra.jdbc")
@Getter
public class JdbcTableConfigs {
    @Inject
    private List<JdbcTableConfig> tableConfigs;

    private MetricConfig metricConfig;

    public JdbcTableConfig tableConfig(String name) {
        return this.tableConfigs
            .stream()
            .filter(tableConfig -> tableConfig.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unable to find table config for name '" + name + "'"));
    }

    public JdbcTableConfig tableConfig(Class<?> cls) {
        return this.tableConfigs
            .stream()
            .filter(tableConfig -> tableConfig.cls() == cls)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unable to find table config for class '" + cls.getName() + "'"));
    }

    @ConfigurationProperties("kestra.jdbc.metrics")
    public record MetricConfig(
        @Bindable(defaultValue = "10") long queryDurationThresholdMs
    ) { }
}
