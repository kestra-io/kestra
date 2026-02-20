package io.kestra.cli;

import ch.qos.logback.classic.LoggerContext;
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

    @Option(names = {"--internal-log"}, description = "Change also log level for internal log")
    private boolean internalLog = false;

    public enum LogLevel {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    protected void initLogger() {
        if (this.verbose.length == 1) {
            this.logLevel = LogLevel.DEBUG;
        } else if (this.verbose.length > 1) {
            this.logLevel = LogLevel.TRACE;
        }

        ((LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory())
            .getLoggerList()
            .stream()
            .filter(logger -> (
                this.internalLog && (
                    logger.getName().startsWith("io.kestra") &&
                        !logger.getName().startsWith("io.kestra.ee.runner.kafka.services"))
            ))
            .forEach(
                logger -> logger.setLevel(ch.qos.logback.classic.Level.valueOf(this.logLevel.name()))
            );
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
