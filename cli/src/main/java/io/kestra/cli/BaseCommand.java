package io.kestra.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.text.MessageFormat;

@Command(
    mixinStandardHelpOptions = true,
    showDefaultValues = true
)
public abstract class BaseCommand {

    @Option(names = {"-v", "--verbose"}, description = "Change log level. Multiple -v options increase the verbosity.", showDefaultValue = CommandLine.Help.Visibility.NEVER)
    protected boolean[] verbose = new boolean[0];

    @Option(names = {"-l", "--log-level"}, description = "Change log level (values: ${COMPLETION-CANDIDATES})")
    protected LogLevel logLevel = LogLevel.INFO;
    
    public enum LogLevel {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    public static String message(String message, Object... format) {
        return CommandLine.Help.Ansi.AUTO.string(
            format.length == 0 ? message : MessageFormat.format(message, format)
        );
    }

    public static void stdOut(String message, Object... format) {
        System.out.println(message(message, format));
    }

    public static void stdErr(String message, Object... format) {
        System.err.println(message(message, format));
    }
}
