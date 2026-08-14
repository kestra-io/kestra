package io.kestra.cli;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import org.slf4j.bridge.SLF4JBridgeHandler;

import io.kestra.cli.commands.configs.sys.ConfigCommand;
import io.kestra.cli.commands.flows.FlowCommand;
import io.kestra.cli.commands.migrations.AbstractMigrationCommand;
import io.kestra.cli.commands.migrations.MigrationCommand;
import io.kestra.cli.commands.namespaces.NamespaceCommand;
import io.kestra.cli.commands.plugins.PluginCommand;
import io.kestra.cli.commands.servers.ServerCommand;
import io.kestra.cli.commands.sys.SysCommand;
import io.kestra.cli.schema.ConfigurationSchemaCommand;
import io.kestra.cli.services.EnvironmentProvider;
import io.kestra.core.models.ServerType;

import io.micronaut.configuration.picocli.MicronautFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.DefaultApplicationContext;
import io.micronaut.context.DefaultApplicationContextBuilder;
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.BeanDefinitionReference;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "kestra",
    versionProvider = VersionProvider.class,
    parameterListHeading = "%nParameters:%n",
    optionListHeading = "%nOptions:%n",
    commandListHeading = "%nCommands:%n",
    mixinStandardHelpOptions = true,
    subcommands = {
        PluginCommand.class,
        ServerCommand.class,
        FlowCommand.class,
        SysCommand.class,
        ConfigCommand.class,
        ConfigurationSchemaCommand.class,
        NamespaceCommand.class,
        MigrationCommand.class
    }
)
public class Kestra implements Callable<Integer> {

    public static void main(String[] args) {
        System.exit(runCli(args));
    }

    public static int runCli(String[] args, String... extraEnvironments) {
        return runCli(Kestra.class, args, extraEnvironments);
    }

    public static int runCli(Class<?> cls, String[] args, String... extraEnvironments) {
        ServiceLoader<EnvironmentProvider> environmentProviders = ServiceLoader.load(EnvironmentProvider.class);
        String[] baseEnvironments = environmentProviders.findFirst().map(EnvironmentProvider::getCliEnvironments).orElseGet(() -> new String[0]);
        return execute(
            cls,
            Stream.concat(
                Arrays.stream(baseEnvironments),
                Arrays.stream(extraEnvironments)
            ).toArray(String[]::new),
            args
        );
    }

    @Override
    public Integer call() throws Exception {
        return runCli(new String[0]);
    }

    protected static int execute(Class<?> cls, String[] environments, String... args) {
        // Log Bridge
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        // Init ApplicationContext
        CommandLine commandLine = getCommandLine(cls, args);

        ApplicationContext applicationContext = Kestra.applicationContext(cls, commandLine, environments);

        Class<?> targetCommand = commandLine.getCommandSpec().userObject().getClass();

        if (!AbstractCommand.class.isAssignableFrom(targetCommand) && args.length == 0) {
            // if no command provided, show help
            args = new String[] { "--help" };
        }

        // Call Picocli command
        int exitCode;
        try {
            exitCode = new CommandLine(cls, new MicronautFactory(applicationContext)).execute(args);
        } catch (CommandLine.InitializationException e) {
            System.err.println("Could not initialize picocli CommandLine, err: " + e.getMessage());
            e.printStackTrace();
            exitCode = 1;
        } catch (Exception e) {
            // A failure while starting the application context (e.g. an invalid configuration
            // triggering a BeanInstantiationException during eager bean init) escapes here.
            // Catch it so the JVM fails fast with a non-zero exit code instead of hanging: the
            // main thread would otherwise die while non-daemon threads from the partially-started
            // context keep the process alive forever.
            System.err.println("Could not start Kestra, err: " + e.getMessage());
            e.printStackTrace();
            exitCode = 1;
        } finally {
            applicationContext.close();
        }

        // exit code
        return exitCode;
    }

    private static CommandLine getCommandLine(Class<?> cls, String[] args) {
        CommandLine cmd = new CommandLine(cls, CommandLine.defaultFactory());
        continueOnParsingErrors(cmd);

        CommandLine.ParseResult parseResult = cmd.parseArgs(args);
        List<CommandLine> parsedCommands = parseResult.asCommandLineList();
        CommandLine leafCmd = parsedCommands.getLast();

        // continueOnParsingErrors silently drops unrecognized options at the root level,
        // including --config/-c when it appears before the subcommand name. Recover it here.
        recoverConfigOption(args, leafCmd);

        return leafCmd;
    }

    /**
     * If {@code --config/-c} was placed before the subcommand name it is silently swallowed by
     * {@code continueOnParsingErrors}. This method scans the raw args for a config path and
     * injects it into the leaf command so that {@code propertiesFromConfig()} picks it up.
     */
    private static void recoverConfigOption(String[] args, CommandLine leafCmd) {
        Object userObject = leafCmd.getCommandSpec().userObject();
        if (!(userObject instanceof AbstractCommand abstractCmd)) {
            return;
        }
        // If --config was already parsed on the leaf command (placed after the subcommand), nothing to do.
        CommandLine.ParseResult leafResult = leafCmd.getParseResult();
        if (
            leafResult != null && leafResult.matchedOptions().stream()
                .anyMatch(opt -> opt.longestName().equals("--config"))
        ) {
            return;
        }
        for (int i = 0; i < args.length - 1; i++) {
            if ("--config".equals(args[i]) || "-c".equals(args[i])) {
                abstractCmd.setConfig(Paths.get(args[i + 1]));
                break;
            }
        }
    }

    public static ApplicationContext applicationContext(Class<?> mainClass,
        String[] environments,
        String... args) {
        return Kestra.applicationContext(mainClass, getCommandLine(mainClass, args), environments);
    }

    /**
     * Create an {@link ApplicationContext} with additional properties based on configuration files (--config) and
     * forced Properties from current command.
     *
     * @return the application context created
     */
    protected static ApplicationContext applicationContext(Class<?> mainClass,
        CommandLine commandLine,
        String[] environments) {

        Class<?> cls = commandLine.getCommandSpec().userObject().getClass();

        Map<String, Object> properties = AbstractCommand.class.isAssignableFrom(cls)
            ? commandProperties(cls, commandLine)
            : Map.of();

        return contextBuilder(cls, properties)
            .mainClass(mainClass)
            .environments(environments)
            .properties(properties)
            .build();
    }

    /**
     * Resolve the properties a command contributes to its {@link ApplicationContext}: the
     * {@code --config} file, the command's forced overrides, and the {@code --port} option.
     */
    private static Map<String, Object> commandProperties(Class<?> cls, CommandLine commandLine) {
        Map<String, Object> properties = new HashMap<>();

        // if class have propertiesFromConfig, add configuration files
        Map<String, Object> configProperties = getPropertiesFromMethod(cls, "propertiesFromConfig", commandLine.getCommandSpec().userObject());
        if (configProperties != null) {
            properties.putAll(configProperties);
        }

        // if class have propertiesOverrides, add force properties for this class
        Map<String, Object> propertiesOverrides = getPropertiesFromMethod(cls, "propertiesOverrides", null);
        if (propertiesOverrides != null && isPracticalCommand(commandLine)) {
            properties.putAll(propertiesOverrides);
        }

        // custom server configuration
        commandLine
            .getParseResult()
            .matchedArgs()
            .stream()
            .filter(argSpec -> ((Field) argSpec.userObject()).getName().equals("serverPort"))
            .findFirst()
            .ifPresent(argSpec -> properties.put("micronaut.server.port", argSpec.getValue()));

        return properties;
    }

    /**
     * Select the {@link ApplicationContext} flavour a command runs in.
     */
    private static ApplicationContextBuilder contextBuilder(Class<?> cls, Map<String, Object> properties) {
        // Pure migration commands run in a minimal context that never registers the other
        // Kestra @Context beans (repositories, server services, the migration startup trigger),
        // so nothing touches the database before the migration is applied explicitly.
        if (AbstractMigrationCommand.class.isAssignableFrom(cls)) {
            return new MigrationApplicationContextBuilder();
        }

        // A worker runs in a context without any datasource at all.
        if (isWorkerServerType(properties)) {
            return new WorkerApplicationContextBuilder();
        }

        return ApplicationContext.builder();
    }

    /**
     * The server type is read from the resolved command properties rather than from the command
     * class, so any command forcing {@code kestra.server-type} to {@code WORKER} — including the EE
     * ones — gets the worker context.
     */
    private static boolean isWorkerServerType(Map<String, Object> properties) {
        return Optional.ofNullable(properties.get("kestra.server-type"))
            .map(String::valueOf)
            .filter(ServerType.WORKER.name()::equalsIgnoreCase)
            .isPresent();
    }

    private static void continueOnParsingErrors(CommandLine cmd) {
        cmd.getCommandSpec().parser().collectErrors(true);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getPropertiesFromMethod(Class<?> cls, String methodName, Object instance) {
        try {
            Method method = cls.getMethod(methodName);
            try {
                return (T) method.invoke(instance);

            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } catch (NoSuchMethodException | SecurityException ignored) {

        }

        return null;
    }

    /**
     * @param commandLine parsed command
     * @return false if the command is a help or version request, true otherwise
     */
    private static boolean isPracticalCommand(CommandLine commandLine) {
        return !(commandLine.isUsageHelpRequested() || commandLine.isVersionHelpRequested());
    }

    /**
     * Builder that produces a {@link MigrationApplicationContext} while applying all the standard
     * properties/environments/property-sources wiring of {@link DefaultApplicationContextBuilder#build()}.
     */
    private static final class MigrationApplicationContextBuilder extends DefaultApplicationContextBuilder {
        @Override
        protected ApplicationContext newApplicationContext() {
            return new MigrationApplicationContext(this);
        }
    }

    /**
     * Builder that produces a {@link WorkerApplicationContext}, see
     * {@link MigrationApplicationContextBuilder} for the wiring it inherits.
     */
    private static final class WorkerApplicationContextBuilder extends DefaultApplicationContextBuilder {
        @Override
        protected ApplicationContext newApplicationContext() {
            return new WorkerApplicationContext(this);
        }
    }

    /**
     * {@link ApplicationContext} for {@code server worker}: it drops Micronaut's JDBC datasource
     * beans so a worker never opens a database connection.
     *
     * <p>
     * A worker owns no repository and reaches the rest of the cluster over gRPC, but Micronaut turns
     * every {@code datasources.<name>} entry into an eagerly-initialized, fail-fast Hikari pool
     * regardless of {@code kestra.server-type}. Deployments commonly share one configuration across
     * all server types, so a worker with no route to the database — the normal case for a remote
     * worker — died at startup on a datasource it never uses.
     *
     * <p>
     * Dropping the bean definitions is the only lever that works for every configuration source:
     * the datasource names are user-chosen ({@code @EachProperty}), so the per-datasource
     * {@code datasources.<name>.enabled=false} switch cannot be forced by the command, whose
     * property overrides are resolved before any configuration is read.
     */
    private static final class WorkerApplicationContext extends DefaultApplicationContext {

        /**
         * Package holding {@code DatasourceConfiguration} and the {@code @Context} factory that
         * turns it into a pool. Matching on the definition name keeps this free of class loading.
         */
        private static final String MICRONAUT_JDBC_PACKAGE = "io.micronaut.configuration.jdbc.";

        WorkerApplicationContext(ApplicationContextConfiguration configuration) {
            super(configuration);
        }

        @Override
        protected List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
            return super.resolveBeanDefinitionReferences().stream()
                .filter(reference -> !reference.getBeanDefinitionName().startsWith(MICRONAUT_JDBC_PACKAGE))
                .toList();
        }
    }

    /**
     * Minimal {@link ApplicationContext} for the {@code kestra migrate} commands: it drops every
     * <em>conditionally-registered</em> Kestra {@code @Context} bean — the migration startup
     * trigger, the server/liveness services, any repository/queue-backed startup bean, and the EE
     * feature validators. Starting the context therefore initializes none of them, so nothing
     * queries the database (or starts a server/network facet) before the migration is applied.
     *
     * <p>
     * Only <em>unconditional</em> {@code @Context} beans survive: those are Micronaut and Kestra
     * DI infrastructure (value extraction, expression evaluation, temp-file config, …) that the
     * command still needs to be instantiated. The migration runner and its lock, history store and
     * {@code DataSource} are lazy {@code @Singleton}s, pulled on demand by the command.
     */
    private static final class MigrationApplicationContext extends DefaultApplicationContext {

        MigrationApplicationContext(ApplicationContextConfiguration configuration) {
            super(configuration);
        }

        @Override
        protected List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
            return super.resolveBeanDefinitionReferences().stream()
                .filter(reference -> !isConditionalKestraStartupBean(reference))
                .toList();
        }

        /**
         * A Kestra {@code @Context} bean whose registration is conditional (carries a
         * {@code @Requires}, directly or via a marker stereotype such as
         * {@code @JdbcRepositoryEnabled}). Such beans exist only to serve a runtime role and must
         * not eager-initialize in a migration context. Dropping <em>any</em> conditional Kestra
         * {@code @Context} bean — rather than denylisting specific gating properties — keeps this
         * robust against beans gated via {@code @Requires(beans = …)}, a different property, or a
         * stereotype. {@code hasStereotype(Requires.class)} is the same signal Micronaut's own
         * {@code RequiresCondition} uses, and it reads the reference metadata without loading the
         * bean class.
         */
        private static boolean isConditionalKestraStartupBean(BeanDefinitionReference<?> reference) {
            return reference.isContextScope()
                && reference.getBeanDefinitionName().startsWith("io.kestra")
                && reference.getAnnotationMetadata().hasStereotype(Requires.class);
        }
    }
}
