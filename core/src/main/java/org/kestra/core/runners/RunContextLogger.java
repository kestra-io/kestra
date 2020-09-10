package org.kestra.core.runners;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.cronutils.utils.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.queues.QueueInterface;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class RunContextLogger {
    private static final int MAX_MESSAGE_LENGTH = 1024*10;
    private final String loggerName;
    private Logger logger;
    private QueueInterface<LogEntry> logQueue;
    private TaskRun taskRun;

    @VisibleForTesting
    public RunContextLogger() {
        this.loggerName = "unit-test";
    }

    public RunContextLogger(QueueInterface<LogEntry> logQueue, TaskRun taskRun) {
        this.loggerName = "flow." + taskRun.getFlowId() + "." + taskRun.getExecutionId() + "." + taskRun.getId();
        this.logQueue = logQueue;
        this.taskRun = taskRun;
    }

    private static List<LogEntry> logEntry(ILoggingEvent event, String message, org.slf4j.event.Level level, TaskRun taskRun) {
        Iterable<String> split;

        if (message.length() > MAX_MESSAGE_LENGTH) {
            split = Splitter.fixedLength(MAX_MESSAGE_LENGTH).split(message);
        } else {
            split = Collections.singletonList(message);
        }

        return StreamSupport.stream(split.spliterator(), false)
            .map(s -> LogEntry.builder()
                .namespace(taskRun.getNamespace())
                .flowId(taskRun.getFlowId())
                .taskId(taskRun.getTaskId())
                .executionId(taskRun.getExecutionId())
                .taskRunId(taskRun.getId())
                .attemptNumber(taskRun.attemptNumber())
                .level(level != null ? level : org.slf4j.event.Level.valueOf(event.getLevel().toString()))
                .message(s)
                .timestamp(Instant.ofEpochMilli(event.getTimeStamp()))
                .thread(event.getThreadName())
                .build()
            )
            .collect(Collectors.toList());
    }

    @SuppressWarnings("UnstableApiUsage")
    public static List<LogEntry> logEntries(ILoggingEvent event, TaskRun taskRun) {
        Throwable throwable = throwable(event);

        if (throwable == null) {
            return logEntry(event, event.getFormattedMessage(), null, taskRun);
        }

        List<LogEntry> result = new ArrayList<>(logEntry(event, event.getMessage(), null, taskRun));

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
                taskRun
            ));
        }

        result.addAll(logEntry(event, Throwables.getStackTraceAsString(throwable), org.slf4j.event.Level.TRACE, taskRun));

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

    public org.slf4j.Logger logger() {
        if (this.logger == null) {
            LoggerContext loggerContext = new LoggerContext();
            this.logger = loggerContext.getLogger(this.loggerName);

            // unit test don't need the logqueue
            if (this.logQueue != null && this.taskRun != null) {
                ContextAppender contextAppender = new ContextAppender(this.logQueue, this.taskRun);
                contextAppender.setContext(loggerContext);
                contextAppender.start();

                this.logger.addAppender(contextAppender);
            }

            ForwardAppender forwardAppender = new ForwardAppender();
            forwardAppender.setContext(loggerContext);
            forwardAppender.start();
            this.logger.addAppender(forwardAppender);

            this.logger.setLevel(Level.TRACE);
            this.logger.setAdditive(true);
        }

        return this.logger;
    }

    public static class ContextAppender extends AppenderBase<ILoggingEvent> {
        private final QueueInterface<LogEntry> logQueue;
        private final TaskRun taskRun;

        public ContextAppender(QueueInterface<LogEntry> logQueue, TaskRun taskRun) {
            this.logQueue = logQueue;
            this.taskRun = taskRun;
        }

        @Override
        protected void append(ILoggingEvent e) {
            logEntries(e, taskRun)
                .forEach(logQueue::emit);
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
            var logger = ((ch.qos.logback.classic.Logger) log);
            if (logger.isEnabledFor(e.getLevel())) {
                ((ch.qos.logback.classic.Logger) log).callAppenders(e);
            }
        }
    }
}
