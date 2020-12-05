package org.kestra.cli.commands.configs.sys;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.order.Ordered;
import io.micronaut.management.endpoint.env.EnvironmentEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.serializers.JacksonMapper;
import picocli.CommandLine;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.inject.Inject;

@CommandLine.Command(
    name = "properties",
    description = {"Display actual configurations properties."}
)
@Slf4j
public class ConfigPropertiesCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    public ConfigPropertiesCommand() {
        super(false);
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        EnvironmentEndpoint endpoint = applicationContext.getBean(EnvironmentEndpoint.class);
        stdOut(JacksonMapper.ofYaml().writeValueAsString(endpoint.getEnvironmentInfo()));

        return 0;
    }
}
