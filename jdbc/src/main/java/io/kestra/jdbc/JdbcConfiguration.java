package io.kestra.jdbc;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;

import java.util.List;
import javax.inject.Inject;

@ConfigurationProperties("kestra.jdbc")
@Getter
public class JdbcConfiguration {
    @Inject
    private List<TableConfig> tableConfigs;

    public TableConfig tableConfig(Class<?> cls) {
        return this.tableConfigs
            .stream()
            .filter(tableConfig -> tableConfig.getCls() == cls)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unable to find table config for class '" + cls.getName() + "'"));
    }
}
