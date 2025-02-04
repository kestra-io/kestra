package io.kestra.core.runners;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.AppenderBase;
import com.cronutils.utils.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueInterface;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class RunContextLogger implements Supplier<org.slf4j.Logger> {
    private static final int MAX_MESSAGE_LENGTH = 1024*10;

    private final String loggerName;
    private volatile Logger logger; // must be volatile as it is built lazily via DCL
    private QueueInterface<LogEntry> logQueue;
    private LogEntry logEntry;
    private Level loglevel;
    private final List<String> useSecrets = new ArrayList<>();
    private final boolean logToFile;
    @Getter
    private File logFile;
    private OutputStream logFileOS;

    @VisibleForTesting
    public RunContextLogger() {
        this.loggerName = "unit-test";
        this.logToFile = false;
    }

    public RunContextLogger(QueueInterface<LogEntry> logQueue, LogEntry logEntry, org.slf4j.event.Level loglevel, boolean logToFile) {
        if (logEntry.getExecutionId() != null) {
            this.loggerName = "flow." + logEntry.getFlowId() + "." + logEntry.getExecutionId() + (logEntry.getTaskRunId() != null ? "." + logEntry.getTaskRunId() : "");
        } else {
            this.loggerName = "flow." + logEntry.getFlowId() + "." + logEntry.getTriggerId();
        }
        this.logQueue = logQueue;
        this.logEntry = logEntry;
        this.loglevel = loglevel == null ? Level.TRACE : Level.toLevel(loglevel.toString());
        this.logToFile = logToFile;
    }

    private static List<LogEntry> logEntry(ILoggingEvent event, String message, org.slf4j.event.Level level, LogEntry logEntry) {
        Iterable<String> split;

        if (message == null) {
            return new ArrayList<>();
        }

        if (message.length() > MAX_MESSAGE_LENGTH) {
            split = Splitter.fixedLength(MAX_MESSAGE_LENGTH).split(message);
        } else {
            split = Collections.singletonList(message);
        }

        List<LogEntry> result = new ArrayList<>();
        long i = 0;
        for (String s : split) {
            result.add(LogEntry.builder()
                .namespace(logEntry.getNamespace())
                .tenantId(logEntry.getTenantId())
                .flowId(logEntry.getFlowId())
                .taskId(logEntry.getTaskId())
                .executionId(logEntry.getExecutionId())
                .taskRunId(logEntry.getTaskRunId())
                .attemptNumber(logEntry.getAttemptNumber())
                .triggerId(logEntry.getTriggerId())
                .level(level != null ? level : org.slf4j.event.Level.valueOf(event.getLevel().toString()))
                .message(s)
                .timestamp(Instant.ofEpochMilli(event.getTimeStamp()).plusMillis(i))
                .thread(event.getThreadName())
                .build()
            );
            i++;
        }

        return result;
    }

    public static List<LogEntry> logEntries(ILoggingEvent event, LogEntry logEntry) {
        Throwable throwable = throwable(event);

        if (throwable == null) {
            return logEntry(event, event.getFormattedMessage(), null, logEntry);
        }

        List<LogEntry> result = new ArrayList<>(logEntry(event, event.getFormattedMessage(), null, logEntry));

        if (Throwables.getCausalChain(throwable).size() > 1 && !(throwable instanceof IllegalVariableEvaluationException)) {
            result.addAll(logEntry(
                event,
                Throwables
                    .getCausalChain(throwable)
                    .stream()
                    .skip(1)
                    .map(Throwable::getMessage)
                    .collect(Collectors.joining("\n")),
                null,
                logEntry
            ));
        }

        result.addAll(logEntry(event, Throwables.getStackTraceAsString(throwable), org.slf4j.event.Level.TRACE, logEntry));

        return result;
    }

    private static Throwable throwable(ILoggingEvent event) {
        Throwable result = null;
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (null != throwableProxy) {
            if (throwableProxy instanceof ThrowableProxy proxyThrowable) {
                result = proxyThrowable.getThrowable();
            }
        }
        return result;
    }

    public void usedSecret(String secret) {
        if (secret != null) {
            this.useSecrets.add(secret);
        }
    }

    public org.slf4j.Logger logger() {
        if (this.logger == null) {
            synchronized (this) {
                if (this.logger == null) { // Double-Checked Locking (DCL) idiom
                    this.logger = initializeLogger();
                }
            }
        }

        return this.logger;
    }

    private Logger initializeLogger() {
        LoggerContext loggerContext = new LoggerContext();
        LogbackMDCAdapter mdcAdapter = new LogbackMDCAdapter();

        loggerContext.setMDCAdapter(mdcAdapter);
        loggerContext.start();

        Logger newLogger = loggerContext.getLogger(this.loggerName);

        if (this.logEntry != null) {
            loggerContext.getMDCAdapter().setContextMap(this.logEntry.toMap());
        }

        // unit tests don't always have the log queue as we construct a logger directly without it
        if (this.logQueue != null && !this.logToFile) {
            ContextAppender contextAppender = new ContextAppender(this, newLogger, this.logQueue, this.logEntry);
            contextAppender.setContext(loggerContext);
            contextAppender.start();

            newLogger.addAppender(contextAppender);
        }

        if (this.logToFile) {
            try {
                this.logFile = File.createTempFile("log", ".txt");
                this.logFileOS = new BufferedOutputStream(new FileOutputStream(logFile));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            FileAppender fileAppender = new FileAppender(this, newLogger, this.logFileOS);
            fileAppender.setContext(loggerContext);
            fileAppender.start();

            newLogger.addAppender(fileAppender);
        }

        // forward flow logs to the server log
        ForwardAppender forwardAppender = new ForwardAppender(this, newLogger);
        forwardAppender.setContext(loggerContext);
        forwardAppender.start();
        newLogger.addAppender(forwardAppender);

        newLogger.setLevel(this.loglevel);
        newLogger.setAdditive(true);
        return newLogger;
    }

    public void resetMDC() {
        this.logger.getLoggerContext().getMDCAdapter().clear();
    }

    @Override
    public org.slf4j.Logger get() {
        return logger();
    }

    public void closeLogFile() throws IOException {
        if (this.logFileOS != null) {
            this.logFileOS.close();
        }
    }

    @Slf4j
    public abstract static class BaseAppender extends AppenderBase<ILoggingEvent> {
        protected RunContextLogger runContextLogger;
        protected Logger logger;

        protected BaseAppender(RunContextLogger runContextLogger, Logger logger) {
            this.runContextLogger = runContextLogger;
            this.logger = logger;
        }

        private String replaceSecret(String data) {
            for (String s : runContextLogger.useSecrets) {
                if (data.contains(s)) {
                    data = data.replace(s, "*".repeat(s.length()));
                    data = data.replaceFirst("[*]{9}", "**masked*");
                }
            }

            return data;
        }

        private Object recursive(Object object) {
            if (object instanceof Map<?, ?> value) {
                return value
                    .entrySet()
                    .stream()
                    .map(e -> new AbstractMap.SimpleEntry<>(
                        recursive(e.getKey()),
                        recursive(e.getValue())
                    ))
                    .collect(HashMap::new, (m, v) -> m.put(v.getKey(), v.getValue()), HashMap::putAll);
            } else if (object instanceof Collection<?> value) {
                return value
                    .stream()
                    .map(this::recursive)
                    .toList();
            } else if (object instanceof String string) {
                return replaceSecret(string);
            } else {
                return object;
            }
        }

        private Object[] replaceSecret(Object[] data) {
            if (data == null) {
                return data;
            }

            Object[] result = new Object[data.length];

            for (int i = 0; i < data.length; i++) {
                result[i] = recursive(data[i]);
            }

            return result;
        }

        protected ILoggingEvent transform(ILoggingEvent event) {
            try {
                String message = replaceSecret(event.getMessage());
                Object[] argumentArray = replaceSecret(event.getArgumentArray());

                return new LoggingEvent(
                    "ch.qos.logback.classic.Logger",
                    this.logger,
                    event.getLevel(),
                    message,
                    event.getThrowableProxy() instanceof ThrowableProxy throwableProxy ? throwableProxy.getThrowable() : null,
                    argumentArray
                );
            } catch (Throwable e) {
                log.warn("Unable to replace secret", e);
                return event;
            }
        }
    }

    public static class ContextAppender extends BaseAppender {
        private final QueueInterface<LogEntry> logQueue;
        private final LogEntry logEntry;

        public ContextAppender(RunContextLogger runContextLogger, Logger logger, QueueInterface<LogEntry> logQueue, LogEntry logEntry) {
            super(runContextLogger, logger);
            this.logQueue = logQueue;
            this.logEntry = logEntry;
        }

        @Override
        protected void append(ILoggingEvent e) {
            e = this.transform(e);

            logEntries(e, logEntry)
                .forEach(log -> {
                    try {
                        logQueue.emitAsync(log);
                    } catch (QueueException ex) {
                        // silently do nothing
                    }
                });
        }
    }

    public static class FileAppender extends BaseAppender {
        private static final ch.qos.logback.classic.Logger LOGGER = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("flow");
        private static final PatternLayout PATTERN_LAYOUT = new PatternLayout();
        static {
            // the pattern is the same as in core/src/main/base.xml except that we remove the coloring
            PATTERN_LAYOUT.setPattern("%d{ISO8601} %-5.5level %-12.36thread %-12.36logger{36} %msg%n");
            PATTERN_LAYOUT.setContext(LOGGER.getLoggerContext());
            PATTERN_LAYOUT.start();
        }

        private final OutputStream fileOS;

        public FileAppender(RunContextLogger runContextLogger, Logger logger, OutputStream fileOS) {
            super(runContextLogger, logger);
            this.fileOS = fileOS;
        }

        @Override
        protected void append(ILoggingEvent e) {
            e = this.transform(e);

            try {
                String line = PATTERN_LAYOUT.doLayout(e);
                fileOS.write(line.getBytes());
            } catch (IOException ex) {
                // silently do nothing, the message will still be forwarded to the server log
            }
        }
    }

    public static class ForwardAppender extends BaseAppender {
        private static final ch.qos.logback.classic.Logger LOGGER = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("flow");

        protected ForwardAppender(RunContextLogger runContextLogger, Logger logger) {
            super(runContextLogger, logger);
        }

        @Override
        public void start() {
            super.start();
        }

        @Override
        public void stop() {
            super.stop();
        }

        @Override
        protected void append(ILoggingEvent e) {
            e = this.transform(e);

            if (LOGGER.isEnabledFor(e.getLevel())) {
                LOGGER.callAppenders(e);
            }
        }
    }
}
