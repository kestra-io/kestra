package io.kestra.cli.commands.flows;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

import org.apache.commons.io.FileUtils;

import com.google.common.collect.ImmutableMap;

import io.kestra.cli.AbstractApiCommand;
import io.kestra.cli.StandAloneRunner;
import io.kestra.cli.services.TenantIdSelectorService;
import io.kestra.core.executor.command.Create;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.models.ServerType;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.utils.IdUtils;
import io.kestra.controller.config.WorkerControllersConfiguration.DiscoveryType;
import io.kestra.jdbc.EphemeralDatabase;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.io.socket.SocketUtils;
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

    // Resolved once per JVM so the forced values stay stable: the database URL is read back by
    // EphemeralDatasourceRewriter, and the storage directory by the cleanup in call().
    private static final Path TEMP_STORAGE = generateTempDir();

    private static final String EPHEMERAL_DATABASE_URL =
        "jdbc:h2:mem:flow-test-%s;LOCK_TIMEOUT=30000;TIME ZONE=UTC;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE".formatted(IdUtils.create());

    /**
     * Kept out of the ephemeral port range (32768-60999 on Linux), where an outbound socket could
     * steal the port between this lookup and the controller binding it.
     */
    private static final int CONTROLLER_PORT = SocketUtils.findAvailableTcpPort(20000, 32000);

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return ImmutableMap.<String, Object> builder()
            // The runner starts the services a standalone server does, and they are only registered
            // for a declared server type.
            .put("kestra.server-type", ServerType.STANDALONE)
            // The flow is read from a file rather than from the instance, so the run gets a database
            // and a storage directory of its own. EphemeralDatabase.URL_PROPERTY repoints every
            // datasource, including the log store's own.
            .put("kestra.repository.type", "h2")
            .put("kestra.queue.type", "h2")
            .put("kestra.logs.type", "h2")
            .put(EphemeralDatabase.URL_PROPERTY, EPHEMERAL_DATABASE_URL)
            .put("kestra.storage.type", "local")
            .put("kestra.storage.local.base-path", TEMP_STORAGE.toAbsolutePath().toString())
            // The worker started here must reach the controller started here. Left on the
            // instance's discovery, a distributed configuration would send it to the real
            // controller, where it would pick up and run production work.
            .put("kestra.worker.controllers.type", DiscoveryType.STATIC)
            .put("kestra.worker.controllers.static.endpoints[0].host", "localhost")
            .put("kestra.worker.controllers.static.endpoints[0].port", CONTROLLER_PORT)
            .put("kestra.controller.port", CONTROLLER_PORT)
            // Testing a flow is not the instance doing work, so it reports no usage.
            .put("kestra.anonymous-usage-report.enabled", false)
            .build();
    }

    private static Path generateTempDir() {
        return Path.of(
            System.getProperty("java.io.tmpdir"),
            FlowTestCommand.class.getSimpleName(),
            String.valueOf(random.nextLong())
        );
    }

    /**
     * The flow runs in this JVM rather than on a server, so its tasks have to be loadable.
     */
    @Override
    protected boolean loadExternalPlugins() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Integer call() throws Exception {
        super.call();

        LocalFlowRepositoryLoader repositoryLoader = applicationContext.getBean(LocalFlowRepositoryLoader.class);
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

        // The database is created empty, so the tenant the flow is loaded under has to be created
        // too. Resolved without the existence check the Enterprise Edition applies, which no row
        // could satisfy here, and created up front because that check runs on every later read.
        String tenant = tenantService.getTenantIdAndAllowEETenants(tenantId);

        try (StandAloneRunner runner = applicationContext.createBean(StandAloneRunner.class);) {
            tenantService.createTenant(tenant);
            runner.run();

            List<FlowWithSource> loaded = repositoryLoader.load(tenant, file.toFile());
            if (loaded.isEmpty()) {
                throw new CommandLine.ParameterException(
                    this.spec.commandLine(),
                    "No valid flow was found in '%s'. The validation errors are reported in the logs above.".formatted(file)
                );
            }
            if (loaded.size() > 1) {
                throw new CommandLine.ParameterException(
                    this.spec.commandLine(),
                    "Found %d flows in '%s' but a test runs a single flow. Pass one flow file.".formatted(loaded.size(), file)
                );
            }

            var flow = loaded.getFirst();
            var createCommand = Create.of(flow.toFlowId()).withInputsFromReader((executionId) -> flowInputOutput.readExecutionInputs(flow, executionId, inputs));
            executionCommandQueue.emit(
                createCommand
            );
            Execution terminated = await().atMost(Duration.ofHours(1)).until(
                () -> executionRepository.findById(tenant, createCommand.executionId()).orElse(null),
                e -> e != null && e.getState().isTerminated()
            );
            stdOut("Successfully executed the flow with execution %s in state %s", terminated.getId(), terminated.getState().getCurrent());
        } catch (ConstraintViolationException e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            // The directory this command generated, so the cleanup does not depend on the storage
            // override having been the one that won.
            FileUtils.deleteQuietly(TEMP_STORAGE.toFile());
        }

        return 0;
    }
}
