package org.floworc.core.runners;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.LogEntry;
import org.floworc.core.models.executions.MetricEntry;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.storages.StorageInterface;
import org.floworc.core.storages.StorageObject;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class RunContext {
    private static Handlebars handlebars = new Handlebars()
        .with(EscapingStrategy.NOOP)
        .registerHelpers(ConditionalHelpers.class)
        .registerHelpers(EachHelper.class)
        .registerHelpers(LogHelper.class)
        .registerHelpers(StringHelpers.class)
        .registerHelpers(UnlessHelper.class)
        .registerHelpers(WithHelper.class)
        .registerHelperMissing((context, options) -> {
            throw new IllegalStateException("Missing variable: " + options.helperName);
        });

    @With
    private StorageInterface storageInterface;

    private URI storageOutputPrefix;

    private Map<String, Object> variables;

    private List<MetricEntry> metrics;

    private ContextAppender contextAppender;

    private Logger logger;

    public RunContext(Flow flow, Task task, Execution execution, TaskRun taskRun) {
        this.storageOutputPrefix = StorageInterface.outputPrefix(flow, task, execution, taskRun);

        ImmutableMap.Builder<String, Object> variblesBuilder = ImmutableMap.<String, Object>builder()
            .put("flow", ImmutableMap.of(
                "id", flow.getId(),
                "namespace", flow.getNamespace()
            ))
            .put("task", ImmutableMap.of(
                "id", task.getId(),
                "type", task.getType()
            ))
            .put("execution", ImmutableMap.of(
                "id", execution.getId(),
                "startDate", execution.getState().startDate()
            ))
            .put("taskrun", ImmutableMap.of(
                "id", taskRun.getId(),
                "startDate", taskRun.getState().startDate()
            ))
            .put("env", System.getenv());

        if (execution.getTaskRunList() != null) {
            variblesBuilder
                .put("outputs", execution
                    .getTaskRunList()
                    .stream()
                    .filter(current -> current.getOutputs() != null)
                    .map(current -> new AbstractMap.SimpleEntry<>(current.getTaskId(), current.getOutputs()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                );
        }

        if (execution.getInputs() != null) {
            variblesBuilder.put("inputs", execution.getInputs());
        }

        this.variables = variblesBuilder.build();
    }

    @VisibleForTesting
    public RunContext(StorageInterface storageInterface, Map<String, Object> variables) {
        this.storageInterface = storageInterface;
        this.storageOutputPrefix = URI.create("");
        this.variables = variables;
    }

    public org.slf4j.Logger logger(Class cls) {
        if (this.logger == null) {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            this.logger = loggerContext.getLogger(cls);

            this.contextAppender = new ContextAppender();
            this.contextAppender.setContext(loggerContext);
            this.contextAppender.start();

            this.logger.addAppender(this.contextAppender);
            this.logger.setLevel(Level.TRACE);
            this.logger.setAdditive(true);
        }

        return this.logger;
    }

    public String render(String inline) throws IOException {
        Template template = handlebars.compileInline(inline);

        return template.apply(this.variables);
    }

    public List<LogEntry> logs() {
        return this.contextAppender
            .events
            .stream()
            .map(event -> LogEntry.builder()
                .level(org.slf4j.event.Level.valueOf(event.getLevel().toString()))
                .message(event.getFormattedMessage())
                .timestamp(Instant.ofEpochMilli(event.getTimeStamp()))
                .thread(event.getThreadName())
                .build()
            )
            .collect(Collectors.toList());
    }

    public InputStream uriToInputStream(URI uri) throws FileNotFoundException {
        if (uri.getScheme().equals("floworc")) {
            return this.storageInterface.get(uri);
        }

        if (uri.getScheme().equals("file")) {
            return new FileInputStream(uri.toString());
        }

        if (uri.getScheme().equals("http")) {
            try {
                return uri.toURL().openStream();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        throw new IllegalArgumentException("Invalid scheme for uri '" + uri + "'");
    }

    public StorageObject putFile(File file) throws IOException {
        URI uri = URI.create(this.storageOutputPrefix.toString());
        URI resolve = uri.resolve(uri.getPath() + "/" + file.getName());

        return this.storageInterface.put(resolve, new FileInputStream(file));
    }

    public List<MetricEntry> metrics() {
        return this.metrics;
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
