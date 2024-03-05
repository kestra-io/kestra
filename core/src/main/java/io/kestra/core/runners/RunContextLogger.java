package io.kestra.core.runners;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.AppenderBase;
import com.cronutils.utils.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.utils.IdUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class RunContextLogger {
    private static final int MAX_MESSAGE_LENGTH = 1024*10;

    private final String loggerName;
    private Logger logger;
    private QueueInterface<LogEntry> logQueue;
    private LogEntry logEntry;
    private Level loglevel;
    private List<String> useSecrets = new ArrayList<>();

    @VisibleForTesting
    public RunContextLogger() {
        this.loggerName = "unit-test";
    }

    public RunContextLogger(QueueInterface<LogEntry> logQueue, LogEntry logEntry, org.slf4j.event.Level loglevel) {
        if (logEntry.getExecutionId() != null) {
            this.loggerName = "flow." + logEntry.getFlowId() + "." + logEntry.getExecutionId() + (logEntry.getTaskRunId() != null ? "." + logEntry.getTaskRunId() : "");
        } else {
            this.loggerName = "flow." + logEntry.getFlowId() + "." + logEntry.getTriggerId();
        }
        this.logQueue = logQueue;
        this.logEntry = logEntry;
        this.loglevel = loglevel == null ? Level.TRACE : Level.toLevel(loglevel.toString());
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

        if (Throwables.getCausalChain(throwable).size() > 1) {
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
            if (throwableProxy instanceof ThrowableProxy) {
                result = ((ThrowableProxy) throwableProxy).getThrowable();
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
            LoggerContext loggerContext = new LoggerContext();
            LogbackMDCAdapter mdcAdapter = new LogbackMDCAdapter();

            loggerContext.setMDCAdapter(mdcAdapter);
            loggerContext.start();

            this.logger = loggerContext.getLogger(this.loggerName);

            // unit test don't need the logqueue
            if (this.logQueue != null && this.logEntry != null) {
                ContextAppender contextAppender = new ContextAppender(this, this.logger, this.logQueue, this.logEntry);
                contextAppender.setContext(loggerContext);
                contextAppender.start();

                this.logger.addAppender(contextAppender);

                MDC.setContextMap(this.logEntry.toMap());
            }

            ForwardAppender forwardAppender = new ForwardAppender(this, this.logger);
            forwardAppender.setContext(loggerContext);
            forwardAppender.start();
            this.logger.addAppender(forwardAppender);

            this.logger.setLevel(this.loglevel);
            this.logger.setAdditive(true);
        }

        return this.logger;
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
                    .collect(Collectors.toList());
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
                    event.getThrowableProxy() instanceof ThrowableProxy ? ((ThrowableProxy) event.getThrowableProxy()).getThrowable() : null,
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
                .forEach(logQueue::emitAsync);
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
