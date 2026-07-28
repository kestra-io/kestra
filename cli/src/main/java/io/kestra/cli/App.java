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
import io.kestra.cli.commands.migrations.MigrationCommand;
import io.kestra.cli.commands.namespaces.NamespaceCommand;
import io.kestra.cli.commands.plugins.PluginCommand;
import io.kestra.cli.commands.servers.ServerCommand;
import io.kestra.cli.commands.sys.SysCommand;
import io.kestra.cli.commands.templates.TemplateCommand;
import io.kestra.cli.services.EnvironmentProvider;

import io.micronaut.configuration.picocli.MicronautFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.core.annotation.Introspected;
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
        TemplateCommand.class,
        SysCommand.class,
        ConfigCommand.class,
        NamespaceCommand.class,
        MigrationCommand.class
    }
)
@Introspected
public class App implements Callable<Integer> {

    public static void main(String[] args) {
        System.exit(runCli(args));
    }

    public static int runCli(String[] args, String... extraEnvironments) {
        return runCli(App.class, args, extraEnvironments);
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

        ApplicationContext applicationContext = App.applicationContext(cls, commandLine, environments);

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
        }
        applicationContext.close();

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
        if (leafResult != null && leafResult.matchedOptions().stream()
                .anyMatch(opt -> opt.longestName().equals("--config"))) {
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
        return App.applicationContext(mainClass, getCommandLine(mainClass, args), environments);
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

        ApplicationContextBuilder builder = ApplicationContext
            .builder()
            .mainClass(mainClass)
            .environments(environments);

        Class<?> cls = commandLine.getCommandSpec().userObject().getClass();

        if (AbstractCommand.class.isAssignableFrom(cls)) {
            // if class have propertiesFromConfig, add configuration files
            builder.properties(getPropertiesFromMethod(cls, "propertiesFromConfig", commandLine.getCommandSpec().userObject()));

            Map<String, Object> properties = new HashMap<>();

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
}
