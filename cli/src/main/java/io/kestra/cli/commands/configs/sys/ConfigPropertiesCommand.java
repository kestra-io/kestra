package io.kestra.cli.commands.configs.sys;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.commands.NoDatabaseCommandInterface;
import io.kestra.core.serializers.JacksonMapper;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "properties",
    description = { "Display current configuration properties." }
)
@Slf4j
public class ConfigPropertiesCommand extends AbstractCommand implements NoDatabaseCommandInterface {
    /**
     * Micronaut's {@code env} endpoint masks every property value unless a custom
     * {@code EnvironmentEndpointFilter} bean unmasks some of them, which Kestra registers none of
     * (in either edition), so every value was always rendered masked. This command reproduces that
     * default instead of also depending on the {@code EnvironmentEndpointFilter} extension point.
     */
    private static final String MASKED_VALUE = "*****";

    @Inject
    private Environment environment;

    @Override
    public Integer call() throws Exception {
        super.call();

        stdOut(JacksonMapper.ofYaml().writeValueAsString(environmentInfo()));

        return 0;
    }

    /**
     * Renders the same shape Micronaut's {@code env} management endpoint would, without depending on
     * that endpoint being enabled: {@code endpoints.env.enabled} defaults to {@code false}, and
     * Kestra intentionally leaves it off since it would otherwise expose the whole configuration over
     * HTTP.
     */
    private Map<String, Object> environmentInfo() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeEnvironments", environment.getActiveNames());
        result.put("packages", environment.getPackages());
        result.put("propertySources", environment.getPropertySources().stream()
            .sorted(Comparator.comparing(PropertySource::getOrder))
            .map(this::propertySourceInfo)
            .toList());
        return result;
    }

    private Map<String, Object> propertySourceInfo(PropertySource propertySource) {
        Map<String, Object> properties = new LinkedHashMap<>();
        propertySource.forEach(key -> properties.put(key, MASKED_VALUE));

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", propertySource.getName());
        info.put("order", propertySource.getOrder());
        info.put("convention", propertySource.getConvention().name());
        info.put("properties", properties);
        return info;
    }
}
