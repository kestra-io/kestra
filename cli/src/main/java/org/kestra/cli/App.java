package org.kestra.cli;

import com.google.common.collect.ImmutableMap;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.env.Environment;
import org.kestra.cli.commands.TestCommand;
import org.kestra.cli.commands.plugins.PluginCommand;
import org.kestra.cli.commands.servers.StandAloneCommand;
import org.kestra.cli.commands.servers.WebServerCommand;
import org.kestra.cli.commands.servers.WorkerCommand;
import picocli.CommandLine;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
    name = "kestra",
    version = "v0.1",

    parameterListHeading = "%nParameters:%n",
    optionListHeading = "%nOptions:%n",
    commandListHeading = "%nCommands:%n",

    mixinStandardHelpOptions = true,
    subcommands = {
        StandAloneCommand.class,
        TestCommand.class,
        WebServerCommand.class,
        WorkerCommand.class,
        PluginCommand.class
    }
)
public class App implements Callable<Object> {
    public static void main(String[] args) {
        ApplicationContext applicationContext = App.applicationContext(args);

        PicocliRunner.call(App.class, applicationContext, args);

        applicationContext.close();
    }

    @Override
    public Object call() throws Exception {
        return PicocliRunner.call(App.class, "--help");
    }

    private static ApplicationContext applicationContext(String[] args) {
        ApplicationContextBuilder builder = ApplicationContext.build(App.class, Environment.CLI);

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
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getPropertiesFromMethod(Class<?> cls, String methodName, Object instance) {
        try {
            Method method = cls.getMethod(methodName);
            try {
                return (Map<String, Object>) method.invoke(instance);

            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } catch (NoSuchMethodException | SecurityException ignored) {

        }

        return ImmutableMap.of();
    }
}
