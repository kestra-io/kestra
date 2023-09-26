package io.kestra.cli.commands.flows;

import com.google.common.collect.ImmutableMap;
import io.kestra.cli.AbstractCommand;
import io.kestra.core.exceptions.MissingRequiredInput;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.runner.memory.MemoryRunner;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javax.validation.ConstraintViolationException;

@CommandLine.Command(
    name = "test",
    description = "test a flow"
)
@Slf4j
public class FlowTestCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Parameters(index = "0", description = "the flow file to test")
    private Path file;

    @CommandLine.Parameters(
        index = "1..*",
        description = "the inputs to pass as key pair value separated by space, " +
            "for input type file, you need to pass an absolute path."
    )
    private List<String> inputs = new ArrayList<>();

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    private static final SecureRandom random = new SecureRandom();

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return ImmutableMap.of(
            "kestra.repository.type", "memory",
            "kestra.queue.type", "memory",
            "kestra.storage.type", "local",
            "kestra.storage.local.base-path", generateTempDir().toAbsolutePath().toString()
        );
    }

    private static Path generateTempDir() {
        return Path.of(
            System.getProperty("java.io.tmpdir"),
            FlowTestCommand.class.getSimpleName(),
            String.valueOf(random.nextLong())
        );
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        MemoryRunner runner = applicationContext.getBean(MemoryRunner.class);
        LocalFlowRepositoryLoader repositoryLoader = applicationContext.getBean(LocalFlowRepositoryLoader.class);
        FlowRepositoryInterface flowRepository = applicationContext.getBean(FlowRepositoryInterface.class);
        RunnerUtils runnerUtils = applicationContext.getBean(RunnerUtils.class);

        Map<String, Object> inputs = new HashMap<>();

        for (int i = 0; i < this.inputs.size(); i=i+2) {
            if (this.inputs.size() <= i + 1) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), "Invalid key pair value for inputs");
            }

            inputs.put(this.inputs.get(i), this.inputs.get(i+1));
        }

        try {
            runner.run();
            repositoryLoader.load(file.toFile());

            List<Flow> all = flowRepository.findAll();
            if (all.size() != 1) {
                throw new IllegalArgumentException("Too many flow found, need 1, found " + all.size());
            }

            runnerUtils.runOne(
                all.get(0),
                (flow, execution) -> runnerUtils.typedInputs(flow, execution, inputs),
                Duration.ofHours(1)
            );

            runner.close();
        } catch (MissingRequiredInput e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage());
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException(e);
        } catch (ConstraintViolationException e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), "Invalid flow", e);
        } finally {
            applicationContext.getProperty("kestra.storage.local.base-path", Path.class)
                .ifPresent(path -> {
                    try {
                        FileUtils.deleteDirectory(path.toFile());
                    } catch (IOException ignored) {
                    }
                });
        }

        return 0;
    }
}
