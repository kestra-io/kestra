package io.kestra.cli.commands.configs.sys;

import java.util.ArrayList;
import java.util.List;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.models.ServerType;
import io.kestra.core.utils.Enums;
import io.kestra.core.validations.AppConfigValidator;
import io.kestra.core.validations.ConfigValidationResult;
import io.kestra.core.validations.ServerCommandValidator;

import io.micronaut.context.env.Environment;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "validate",
    description = { "Validate the current configuration." }
)
@Slf4j
public class ConfigValidateCommand extends AbstractCommand {
    @Inject
    private Environment environment;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(
        names = { "--server-type" },
        description = "Also validate the properties required to start the given server type (values: ${COMPLETION-CANDIDATES})."
    )
    private String serverType;

    @Override
    public Integer call() throws Exception {
        super.call();

        final List<ConfigValidationResult> results = new ArrayList<>(AppConfigValidator.validateConfiguration(environment));

        if (serverType != null) {
            results.addAll(ServerCommandValidator.validateServerConfiguration(environment, parseServerType()));
        }

        results.stream()
            .filter(ConfigValidationResult::valid)
            .forEach(result -> stdOut("@|green ✓|@ - {0}", result.key()));

        final List<ConfigValidationResult> failures = results.stream()
            .filter(result -> !result.valid())
            .toList();

        failures.forEach(result ->
        {
            stdErr("@|red ✘|@ - {0}", result.key());
            stdErr("\t- @|bold,yellow {0}|@", result.message().replace("\n", " - "));
        });

        if (failures.isEmpty()) {
            stdOut("@|green Configuration is valid.|@");
            return 0;
        }

        stdErr("@|red Configuration is invalid: {0} error(s) found.|@", failures.size());
        return 1;
    }

    private ServerType parseServerType() {
        try {
            return Enums.getForNameIgnoreCase(serverType, ServerType.class);
        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage());
        }
    }
}
