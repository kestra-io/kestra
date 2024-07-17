package io.kestra.cli;

import io.kestra.cli.commands.configs.sys.ConfigCommand;
import io.kestra.cli.commands.flows.FlowCommand;
import io.kestra.cli.commands.namespaces.NamespaceCommand;
import io.kestra.cli.commands.plugins.PluginCommand;
import io.kestra.cli.commands.servers.AbstractServerCommand;
import io.kestra.cli.commands.servers.ServerCommand;
import io.kestra.cli.commands.sys.SysCommand;
import io.kestra.cli.commands.templates.TemplateCommand;
import io.micronaut.configuration.picocli.MicronautFactory;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Introspected;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.bridge.SLF4JBridgeHandler;
import picocli.CommandLine;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Callable;

@CommandLine.Command(
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
    }
)
@Slf4j
@Introspected
public class App implements Callable<Integer> {
    public static void main(String[] args) {
        execute(App.class, args);
    }

    @Override
    public Integer call() throws Exception {
        return PicocliRunner.call(App.class, "--help");
    }

    protected static void execute(Class<?> cls, String... args) {
        // Log Bridge
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        // Init ApplicationContext
        ApplicationContext applicationContext = App.applicationContext(cls, args);

        // Call Picocli command
        int exitCode = new CommandLine(cls, new MicronautFactory(applicationContext)).execute(args);

        applicationContext.close();

        // exit code
        System.exit(Objects.requireNonNullElse(exitCode, 0));
    }

    /**
     * Create an {@link ApplicationContext} with additional properties based on configuration files (--config) and
     * forced Properties from current command.
     *
     * @param args args passed to java app
     * @return the application context created
     */
    protected static ApplicationContext applicationContext(Class<?> mainClass, String[] args) {
        ApplicationContextBuilder builder = ApplicationContext
            .builder()
            .mainClass(mainClass)
            .environments(Environment.CLI);

        CommandLine cmd = new CommandLine(mainClass, CommandLine.defaultFactory());

        CommandLine.ParseResult parseResult = cmd.parseArgs(args);
        List<CommandLine> parsedCommands = parseResult.asCommandLineList();

        CommandLine commandLine = parsedCommands.get(parsedCommands.size() - 1);
        Class<?> cls = commandLine.getCommandSpec().userObject().getClass();

        if (AbstractCommand.class.isAssignableFrom(cls)) {
            Map<String, Object> properties = new HashMap<>();

            // if class have propertiesFromConfig, add configuration files
            addPropertiesFromMethod(properties, cls, "propertiesFromConfig", commandLine.getCommandSpec().userObject());
            // if class have propertiesOverrides, add force properties for this class
            addPropertiesFromMethod(properties, cls, "propertiesOverrides", null);

            if (AbstractServerCommand.class.isAssignableFrom(cls)) {
                validateServerCmdConfig(properties);
            }

            // custom server configuration
            commandLine
                .getParseResult()
                .matchedArgs()
                .stream()
                .filter(argSpec -> ((Field) argSpec.userObject()).getName().equals("serverPort"))
                .findFirst()
                .ifPresent(argSpec -> {
                    properties.put("micronaut.server.port", argSpec.getValue());
                });

            builder.properties(properties);
        }
        return builder.build();
    }

    private static void addPropertiesFromMethod(Map<String, Object> properties,  Class<?> cls, String methodName, Object instance) {
        Map<String, Object> propertiesFromMethod = getPropertiesFromMethod(cls, methodName, instance);
        if (propertiesFromMethod != null) {
            properties.putAll(propertiesFromMethod);
        }
    }

    private static void validateServerCmdConfig(Map<String, Object> propertiesFromConfig) {
        final Map<String, String> requiredProperties = Map.of(
            "kestra.queue.type", "https://kestra.io/docs/configuration-guide/setup#queue-configuration",
            "kestra.repository.type", "https://kestra.io/docs/configuration-guide/setup#repository-configuration",
            "kestra.storage.type", "https://kestra.io/docs/configuration-guide/setup#internal-storage-configuration"
        );

        final List<Map.Entry<String, String>> missingProperties = requiredProperties.entrySet().stream()
            .filter((property) -> !propertiesFromConfig.containsKey(property.getKey()))
            .toList();

        missingProperties.forEach(property -> log.error("""
            Server configuration requires the '{}' property to be defined.
            For more details, please follow the official setup guide at: {}""", property.getKey(), property.getValue())
        );

        if (!missingProperties.isEmpty()) {
            throw new AbstractServerCommand.ServerCommandException("Incomplete server configuration - missing required properties");
        }
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
}
