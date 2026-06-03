package io.kestra.cli.commands.flows;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

import io.kestra.core.executor.command.Create;
import io.kestra.core.executor.command.ExecutionCommand;
import org.apache.commons.io.FileUtils;

import com.google.common.collect.ImmutableMap;

import io.kestra.cli.AbstractApiCommand;
import io.kestra.cli.StandAloneRunner;
import io.kestra.cli.services.TenantIdSelectorService;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.FlowInputOutput;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import static org.awaitility.Awaitility.await;

@CommandLine.Command(
    name = "test",
    description = "Test a flow"
)
@Slf4j
public class FlowTestCommand extends AbstractApiCommand {
    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Parameters(index = "0", description = "The flow file to test")
    private Path file;

    @CommandLine.Parameters(
        index = "1..*",
        description = "The inputs to pass as key pair value separated by space, " +
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
    @SuppressWarnings("unchecked")
    public Integer call() throws Exception {
        super.call();

        LocalFlowRepositoryLoader repositoryLoader = applicationContext.getBean(LocalFlowRepositoryLoader.class);
        FlowRepositoryInterface flowRepository = applicationContext.getBean(FlowRepositoryInterface.class);
        ExecutionRepositoryInterface executionRepository = applicationContext.getBean(ExecutionRepositoryInterface.class);
        FlowInputOutput flowInputOutput = applicationContext.getBean(FlowInputOutput.class);
        TenantIdSelectorService tenantService = applicationContext.getBean(TenantIdSelectorService.class);
        DispatchQueueInterface<ExecutionCommand> executionCommandQueue = applicationContext.getBean(DispatchQueueInterface.class, Qualifiers.byTypeArguments(ExecutionCommand.class));

        Map<String, Object> inputs = new HashMap<>();

        for (int i = 0; i < this.inputs.size(); i = i + 2) {
            if (this.inputs.size() <= i + 1) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), "Invalid key pair value for inputs");
            }

            inputs.put(this.inputs.get(i), this.inputs.get(i + 1));
        }

        try (StandAloneRunner runner = applicationContext.createBean(StandAloneRunner.class);) {
            runner.run();
            repositoryLoader.load(tenantService.getTenantId(tenantId), file.toFile());

            List<Flow> all = flowRepository.findAllForAllTenants();
            if (all.size() != 1) {
                throw new IllegalArgumentException("Too many flow found, need 1, found " + all.size());
            }

            var flow = all.getFirst();
            var createCommand = Create.of(flow.toFlowId()).withInputsFromReader((executionId) -> flowInputOutput.readExecutionInputs(flow, executionId, inputs));
            executionCommandQueue.emit(
                createCommand
            );
            Execution terminated = await().atMost(Duration.ofHours(1)).until(
                () -> executionRepository.findById(tenantService.getTenantId(tenantId), createCommand.executionId()).orElse(null),
                e -> e != null && e.getState().isTerminated()
            );
            stdOut("Successfully executed the flow with execution %s in state %s", terminated.getId(), terminated.getState().getCurrent());
        } catch (ConstraintViolationException e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            applicationContext.getProperty("kestra.storage.local.base-path", Path.class)
                .ifPresent(path ->
                {
                    try {
                        FileUtils.deleteDirectory(path.toFile());
                    } catch (IOException ignored) {
                    }
                });
        }

        return 0;
    }
}
