package org.kestra.core.runners;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.google.common.base.Throwables;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.queues.QueueInterface;

import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RunContextLogger {
    private Logger logger;
    private QueueInterface<LogEntry> logQueue;
    private String loggerName;
    private TaskRun taskRun;

    @Deprecated
    public RunContextLogger(String loggerName) {
        this.loggerName = loggerName;
    }

    public RunContextLogger(QueueInterface<LogEntry> logQueue, TaskRun taskRun) {
        this.loggerName = "flow." + taskRun.getFlowId() + "." + taskRun.getExecutionId() + "." + taskRun.getId();
        this.logQueue = logQueue;
        this.taskRun = taskRun;
    }

    private static LogEntry logEntry(ILoggingEvent event, String message, org.slf4j.event.Level level, TaskRun taskRun) {
        return LogEntry.builder()
            .namespace(taskRun.getNamespace())
            .flowId(taskRun.getFlowId())
            .taskId(taskRun.getTaskId())
            .executionId(taskRun.getExecutionId())
            .taskRunId(taskRun.getId())
            .attemptNumber(taskRun.attemptNumber())
            .level(level != null ? level : org.slf4j.event.Level.valueOf(event.getLevel().toString()))
            .message(message)
            .timestamp(Instant.ofEpochMilli(event.getTimeStamp()))
            .thread(event.getThreadName())
            .build();
    }

    @SuppressWarnings("UnstableApiUsage")
    public static Stream<LogEntry> logEntries(ILoggingEvent event, TaskRun taskRun) {
        Throwable throwable = throwable(event);

        if (throwable == null) {
            return Stream.of(logEntry(event, event.getFormattedMessage(), null, taskRun));
        }

        Stream.Builder<LogEntry> builder = Stream.<LogEntry>builder()
            .add(logEntry(event, event.getMessage(), null, taskRun));

        if (Throwables.getCausalChain(throwable).size() > 1) {
            builder.add(logEntry(
                event,
                Throwables
                    .getCausalChain(throwable)
                    .stream()
                    .skip(1)
                    .map(Throwable::getMessage)
                    .collect(Collectors.joining("\n")),
                null,
                taskRun
            ));
        }

        return builder
            .add(logEntry(event, Throwables.getStackTraceAsString(throwable), org.slf4j.event.Level.TRACE, taskRun))
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

    public org.slf4j.Logger logger() {
        if (this.logger == null) {
            LoggerContext loggerContext = new LoggerContext();
            this.logger = loggerContext.getLogger(this.loggerName);

            ContextAppender contextAppender = new ContextAppender(this.logQueue, this.taskRun);
            contextAppender.setContext(loggerContext);
            contextAppender.start();

            ForwardAppender forwardAppender = new ForwardAppender();
            forwardAppender.setContext(loggerContext);
            forwardAppender.start();

            this.logger.addAppender(contextAppender);
            this.logger.addAppender(forwardAppender);
            this.logger.setLevel(Level.TRACE);
            this.logger.setAdditive(true);
        }

        return this.logger;
    }

    public static class ContextAppender extends AppenderBase<ILoggingEvent> {
        private QueueInterface<LogEntry> logQueue;
        private TaskRun taskRun;

        public ContextAppender(QueueInterface<LogEntry> logQueue, TaskRun taskRun) {
            this.logQueue = logQueue;
            this.taskRun = taskRun;
        }

        @Override
        protected void append(ILoggingEvent e) {
            logEntries(e, taskRun)
                .forEach(l -> logQueue.emit(l));
        }
    }

    @Slf4j
    public static class ForwardAppender extends AppenderBase<ILoggingEvent> {
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
            ((ch.qos.logback.classic.Logger) log).callAppenders(e);
        }
    }
}
