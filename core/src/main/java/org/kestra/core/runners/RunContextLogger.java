package org.kestra.core.runners;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.google.common.base.Throwables;
import lombok.NoArgsConstructor;
import org.kestra.core.models.executions.LogEntry;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@NoArgsConstructor
public class RunContextLogger {
    private Logger logger;
    private String loggerName;
    private ContextAppender contextAppender;

    public RunContextLogger(String loggerName) {
        this.loggerName = loggerName;
    }

    public List<LogEntry> logs() {
        if (this.contextAppender == null) {
            return new ArrayList<>();
        }

        return this.contextAppender
            .events
            .stream()
            .flatMap(RunContextLogger::logEntries)
            .collect(Collectors.toList());
    }

    private static LogEntry logEntry(ILoggingEvent event, String message, org.slf4j.event.Level level) {
        return LogEntry.builder()
            .level(level != null ? level : org.slf4j.event.Level.valueOf(event.getLevel().toString()))
            .message(message)
            .timestamp(Instant.ofEpochMilli(event.getTimeStamp()))
            .thread(event.getThreadName())
            .build();
    }

    @SuppressWarnings("UnstableApiUsage")
    private static Stream<LogEntry> logEntries(ILoggingEvent event) {
        Throwable throwable = throwable(event);

        if (throwable == null) {
            return Stream.of(logEntry(event, event.getFormattedMessage(), null));
        }

        Stream.Builder<LogEntry> builder = Stream.<LogEntry>builder()
            .add(logEntry(event, event.getMessage(), null));

        if (Throwables.getCausalChain(throwable).size() > 1) {
            builder.add(logEntry(
                event,
                Throwables
                    .getCausalChain(throwable)
                    .stream()
                    .skip(1)
                    .map(Throwable::getMessage)
                    .collect(Collectors.joining("\n")),
                null
            ));
        }

        return builder
            .add(logEntry(event, Throwables.getStackTraceAsString(throwable), org.slf4j.event.Level.TRACE))
            .build();
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

    public org.slf4j.Logger logger(Class<?> cls) {
        if (this.logger == null) {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            this.logger = loggerContext.getLogger(this.loggerName != null ? loggerName : cls.getName());

            this.contextAppender = new ContextAppender();
            this.contextAppender.setContext(loggerContext);
            this.contextAppender.start();

            this.logger.addAppender(this.contextAppender);
            this.logger.setLevel(Level.TRACE);
            this.logger.setAdditive(true);
        }

        return this.logger;
    }

    public static class ContextAppender extends AppenderBase<ILoggingEvent> {
        private final ConcurrentLinkedQueue<ILoggingEvent> events = new ConcurrentLinkedQueue<>();

        @Override
        public void start() {
            super.start();
        }

        @Override
        public void stop() {
            super.stop();
            events.clear();
        }

        @Override
        protected void append(ILoggingEvent e) {
            events.add(e);
        }
    }
}
