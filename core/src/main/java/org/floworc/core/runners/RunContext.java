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
import io.micronaut.context.ApplicationContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.LogEntry;
import org.floworc.core.models.executions.MetricEntry;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.models.tasks.ResolvedTask;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.core.runners.handlebars.helpers.InstantHelper;
import org.floworc.core.storages.StorageInterface;
import org.floworc.core.storages.StorageObject;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
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
        .registerHelpers(InstantHelper.class)
        .registerHelperMissing((context, options) -> {
            throw new IllegalStateException("Missing variable: " + options.helperName);
        });

    @With
    private StorageInterface storageInterface;

    @With
    private ApplicationContext applicationContext;

    private URI storageOutputPrefix;

    private Map<String, Object> variables;

    private List<MetricEntry> metrics;

    private ContextAppender contextAppender;

    private Logger logger;


    public RunContext(Flow flow, Execution execution) {
        this.variables = this.variables(flow, null, execution, null);
    }

    public RunContext(Flow flow, ResolvedTask task, Execution execution, TaskRun taskRun) {
        this.storageOutputPrefix = StorageInterface.outputPrefix(flow, task, execution, taskRun);
        this.variables = this.variables(flow, task, execution, taskRun);
    }

    private Map<String, Object> variables(Flow flow, ResolvedTask resolvedTask, Execution execution, TaskRun taskRun) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
            .put("flow", ImmutableMap.of(
                "id", flow.getId(),
                "namespace", flow.getNamespace()
            ))
            .put("execution", ImmutableMap.of(
                "id", execution.getId(),
                "startDate", execution.getState().startDate()
            ))
            .put("env", System.getenv());

        if (resolvedTask != null) {
            builder
                .put("task", ImmutableMap.of(
                    "id", resolvedTask.getTask().getId(),
                    "type", resolvedTask.getTask().getType()
                ));
        }

        if (taskRun != null) {
            ImmutableMap.Builder<String, Object> taskBuilder = ImmutableMap.<String, Object>builder()
                .put("id", taskRun.getId())
                .put("startDate", taskRun.getState().startDate());

            if (taskRun.getParentTaskRunId() != null) {
                taskBuilder.put("parentId", taskRun.getParentTaskRunId());
            }

            if (taskRun.getValue() != null) {
                taskBuilder.put("value", taskRun.getValue());
            }

            builder.put("taskrun", taskBuilder.build());
        }

        if (execution.getTaskRunList() != null) {
            builder.put("outputs", execution.outputs());
        }

        if (execution.getInputs() != null) {
            builder.put("inputs", execution.getInputs());
        }

        return builder.build();
    }

    @VisibleForTesting
    public RunContext(ApplicationContext applicationContext, Map<String, Object> variables) {
        this.applicationContext = applicationContext;
        this.storageInterface = applicationContext.getBean(StorageInterface.class);
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

    public List<String> render(List<String> list) throws IOException {
        List<String> result = new ArrayList<>();

        for (String inline : list) {
            result.add(this.render(inline));
        }

        return result;
    }

    public List<LogEntry> logs() {
        if (this.contextAppender == null) {
            return new ArrayList<>();
        }

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
