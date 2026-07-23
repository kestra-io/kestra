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

        // Pure migration commands run in a minimal context that never registers the other
        // Kestra @Context beans (repositories, server services, the migration startup trigger),
        // so nothing touches the database before the migration is applied explicitly.
        ApplicationContextBuilder builder = (AbstractMigrationCommand.class.isAssignableFrom(cls)
            ? new MigrationApplicationContextBuilder()
            : ApplicationContext.builder())
            .mainClass(mainClass)
            .environments(environments);

        if (AbstractCommand.class.isAssignableFrom(cls)) {
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

            builder.properties(properties);
        }
        return builder.build();
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
     * Minimal {@link ApplicationContext} for the {@code kestra migrate} commands: it drops the
     * eager {@code @Context} beans that only exist to serve a running server or a data/queue role
     * (see {@link #RUNTIME_STARTUP_PROPERTIES}), including the migration startup trigger. Starting
     * the context therefore initializes none of them, so nothing queries the database before the
     * migration is applied. Framework and DI-infrastructure {@code @Context} beans (e.g. value
     * extraction, expression evaluation) are kept, and the lazily-resolved migration runner, lock,
     * history store and {@code DataSource} are pulled on demand by the command.
     */
    private static final class MigrationApplicationContext extends DefaultApplicationContext {
        /**
         * Properties whose presence activates a runtime (server/data/queue) role. An eager
         * {@code @Context} bean gated on any of them is a startup bean we must not initialize in
         * the migration context — otherwise it would connect to the database (or start a server
         * facet) before the migration runs.
         */
        private static final Set<String> RUNTIME_STARTUP_PROPERTIES = Set.of(
            "kestra.repository.type",
            "kestra.queue.type",
            "kestra.server-type"
        );

        MigrationApplicationContext(ApplicationContextConfiguration configuration) {
            super(configuration);
        }

        @Override
        protected List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
            return super.resolveBeanDefinitionReferences().stream()
                .filter(reference -> !isRuntimeStartupBean(reference))
                .toList();
        }

        private static boolean isRuntimeStartupBean(BeanDefinitionReference<?> reference) {
            if (!reference.isContextScope()) {
                return false;
            }
            return reference.getAnnotationMetadata()
                .getAnnotationValuesByType(Requires.class)
                .stream()
                .anyMatch(
                    requires -> requires.stringValue("property")
                        .map(RUNTIME_STARTUP_PROPERTIES::contains)
                        .orElse(false)
                );
        }
    }
}
