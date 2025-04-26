package io.kestra.cli.commands.configs.sys;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.context.ApplicationContext;
import io.micronaut.management.endpoint.env.EnvironmentEndpoint;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "properties",
    description = {"Display current configuration properties."}
)
@Slf4j
public class ConfigPropertiesCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    @Override
    public Integer call() throws Exception {
        super.call();

        EnvironmentEndpoint endpoint = applicationContext.getBean(EnvironmentEndpoint.class);
        stdOut(JacksonMapper.ofYaml().writeValueAsString(endpoint.getEnvironmentInfo()));

        return 0;
    }
}
