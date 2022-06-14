package io.kestra.jdbc;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.inject.Inject;
import lombok.Getter;

import java.util.List;

@ConfigurationProperties("kestra.jdbc")
@Getter
public class JdbcConfiguration {
    @Inject
    private List<JdbcTableConfig> tableConfigs;

    public JdbcTableConfig tableConfig(String name) {
        return this.tableConfigs
            .stream()
            .filter(tableConfig -> tableConfig.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unable to find table config for name '" + name + "'"));
    }

    public JdbcTableConfig tableConfig(Class<?> cls) {
        return this.tableConfigs
            .stream()
            .filter(tableConfig -> tableConfig.getCls() == cls)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unable to find table config for class '" + cls.getName() + "'"));
    }
}
