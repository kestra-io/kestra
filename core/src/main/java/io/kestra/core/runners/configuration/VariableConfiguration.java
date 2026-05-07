package io.kestra.core.runners.configuration;

import java.util.List;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;

@Getter
@ConfigurationProperties("kestra.variables")
public class VariableConfiguration {
    public VariableConfiguration() {
        this.cacheEnabled = true;
        this.cacheSize = 1000;
        this.recursiveRendering = false;
        this.redactedEnvVars = List.of(
            "KESTRA_PLUGINS_PATH", "KESTRA_CONFIGURATION_PATH", "KESTRA_CONFIGURATION", "KESTRA_JAVA_OPTS"
        );
    }

    Boolean cacheEnabled;
    Integer cacheSize;
    Boolean recursiveRendering;
    List<String> redactedEnvVars;
}
