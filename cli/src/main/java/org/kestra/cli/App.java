package org.kestra.cli;

import io.micronaut.configuration.picocli.MicronautFactory;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.kestra.cli.commands.TestCommand;
import org.kestra.cli.commands.flows.FlowCommand;
import org.kestra.cli.commands.plugins.PluginCommand;
import org.kestra.cli.commands.servers.ServerCommand;
import org.kestra.cli.commands.sys.SysCommand;
import org.kestra.core.contexts.KestraApplicationContextBuilder;
import org.kestra.core.contexts.KestraClassLoader;
import picocli.CommandLine;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
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
        TestCommand.class,
        SysCommand.class
    }
)
public class App implements Callable<Integer> {
    public static void main(String[] args) {
        execute(App.class, args);
    }

    @Override
    public Integer call() throws Exception {
        return PicocliRunner.call(App.class, "--help");
    }

    protected static void execute(Object cls, String... args) {
        // Register a ClassLoader with isolation for plugins
        Thread.currentThread().setContextClassLoader(KestraClassLoader.create(Thread.currentThread().getContextClassLoader()));

        // Init ApplicationContext
        ApplicationContext applicationContext = App.applicationContext(args);

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
    protected static ApplicationContext applicationContext(String[] args) {
        KestraApplicationContextBuilder builder = new KestraApplicationContextBuilder()
            .mainClass(App.class)
            .environments(Environment.CLI)
            .classLoader(KestraClassLoader.instance());

        CommandLine cmd = new CommandLine(App.class, CommandLine.defaultFactory());

        CommandLine.ParseResult parseResult = cmd.parseArgs(args);
        List<CommandLine> parsedCommands = parseResult.asCommandLineList();

        CommandLine commandLine = parsedCommands.get(parsedCommands.size() - 1);
        Class<?> cls = commandLine.getCommandSpec().userObject().getClass();

        if (AbstractCommand.class.isAssignableFrom(cls)) {
            // if class have propertiesFromConfig, add configuration files
            builder.properties(getPropertiesFromMethod(cls, "propertiesFromConfig", commandLine.getCommandSpec().userObject()));

            // if class have propertiesOverrides, add force properties for this class
            builder.properties(getPropertiesFromMethod(cls, "propertiesOverrides", null));

            // add plugins registry if plugin path defined
            builder.pluginRegistry(getPropertiesFromMethod(cls, "initPluginRegistry", commandLine.getCommandSpec().userObject()));
        }

        return builder.build();
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
