package io.kestra.core.runners;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class RunContextCache {
    // List of env variables that should be redacted from the execution run context variables to avoid information disclosure.
    @Value("${kestra.variables.redacted-env-vars:KESTRA_PLUGINS_PATH,KESTRA_CONFIGURATION_PATH,KESTRA_CONFIGURATION}")
    private List<String> redactedEnvVar;

    @Inject
    private ApplicationContext applicationContext;

    @Getter
    private Map<?, ?> globalVars = null;

    @Getter
    private Map<String, String> envVars = null;

    @PostConstruct
    void init() {
        String envPrefix = applicationContext.getProperty("kestra.variables.env-vars-prefix", String.class, "KESTRA_");
        envVars = this.envVariables(envPrefix);

        globalVars = applicationContext
            .getProperty("kestra.variables.globals", Map.class)
            .orElseGet(Map::of);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, String> envVariables(String envPrefix) {
        Map<String, String> result = new HashMap<>(System.getenv());
        result.putAll((Map) System.getProperties());

        return result
            .entrySet()
            .stream()
            .filter(e -> !redactedEnvVar.contains(e.getKey()) && e.getKey().startsWith(envPrefix))
            .map(e -> new AbstractMap.SimpleEntry<>(
                e.getKey().substring(envPrefix.length()).toLowerCase(),
                e.getValue()
            ))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
