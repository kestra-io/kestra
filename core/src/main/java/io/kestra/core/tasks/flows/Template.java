package io.kestra.core.tasks.flows;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.templates.TemplateEnabled;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.GraphUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Slf4j
@Schema(
    title = "Include a reusable template inside a flow"
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "id: template",
                "namespace: io.kestra.tests",
                "",
                "inputs:",
                "  - name: with-string",
                "    type: STRING",
                "",
                "tasks:",
                "  - id: 1-return",
                "    type: io.kestra.core.tasks.debugs.Return",
                "    format: \"{{task.id}} > {{taskrun.startDate}}\"",
                "  - id: 2-template",
                "    type: io.kestra.core.tasks.flows.Template",
                "    namespace: io.kestra.tests",
                "    templateId: template",
                "    args:",
                "      my-forward: \"{{ inputs.with-string }}\"",
                "  - id: 3-end",
                "    type: io.kestra.core.tasks.debugs.Return",
                "    format: \"{{task.id}} > {{taskrun.startDate}}\"\n"
            }
        )
    }
)
@TemplateEnabled
public class Template extends Task implements FlowableTask<Template.Output> {
    @Valid
    @PluginProperty
    protected List<Task> errors;

    @NotNull
    @Schema(
        title = "The namespace of the template"
    )
    @PluginProperty
    private String namespace;

    @NotNull
    @Schema(
        title = "The id of the template"
    )
    @PluginProperty
    private String templateId;

    @Schema(
        title = "The args to pass to the template",
        description = "You can provide a list of named arguments (like function argument on dev) allowing to rename " +
            "outputs of current flow for this template.\n" +
            "for example, if you declare this use of template like this: \n" +
            "```yaml\n" +
            "  - id: 2-template\n" +
            "    type: io.kestra.core.tasks.flows.Template\n" +
            "    namespace: io.kestra.tests\n" +
            "    templateId: template\n" +
            "    args:\n" +
            "      forward: \"{{ output.task-id.uri }}\"\n" +
            "```\n" +
            "You will be able to get this output on the template with `{{ parent.outputs.args.forward }}`"
    )
    @PluginProperty(dynamic = true, additionalProperties = String.class)
    private Map<String, String> args;

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.SEQUENTIAL);
        io.kestra.core.models.templates.Template template = this.findTemplate(ContextHelper.context());

        GraphUtils.sequential(
            subGraph,
            template.getTasks(),
            template.getErrors(),
            taskRun,
            execution
        );

        return subGraph;
    }

    @Override
    public List<Task> allChildTasks() {
        try {
            io.kestra.core.models.templates.Template template = this.findTemplate(ContextHelper.context());

            return Stream
                .concat(
                    template.getTasks() != null ? template.getTasks().stream() : Stream.empty(),
                    template.getErrors() != null ? template.getErrors().stream() : Stream.empty()
                )
                .collect(Collectors.toList());
        } catch (IllegalVariableEvaluationException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        io.kestra.core.models.templates.Template template = this.findTemplate(ContextHelper.context());

        return FlowableUtils.resolveTasks(template.getTasks(), parentTaskRun);
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        io.kestra.core.models.templates.Template template = this.findTemplate(ContextHelper.context());

        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(template.getErrors(), parentTaskRun),
            parentTaskRun
        );
    }

    @Override
    public Template.Output outputs(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        Output.OutputBuilder builder = Output.builder();

        if (this.args != null) {
            builder.args(runContext.render(this.args
                .entrySet()
                .stream()
                .map(throwFunction(e -> new AbstractMap.SimpleEntry<>(
                    e.getKey(),
                    runContext.render(e.getValue())
                )))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
            ));
        }

        return builder.build();
    }

    protected io.kestra.core.models.templates.Template findTemplate(ApplicationContext applicationContext) throws IllegalVariableEvaluationException {
        if (!applicationContext.containsBean(TemplateExecutorInterface.class)) {
            throw new DeserializationException("Templates are disabled, please check your configuration");
        }

        TemplateExecutorInterface templateExecutor = applicationContext.getBean(TemplateExecutorInterface.class);

        return templateExecutor.findById(this.namespace, this.templateId)
            .orElseThrow(() -> new IllegalVariableEvaluationException("Can't find flow template '" + this.namespace + "." + this.templateId + "'"));
    }

    @SuperBuilder(toBuilder = true)
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Hidden
    public static class ExecutorTemplate extends Template {
        private io.kestra.core.models.templates.Template template;

        @Override
        protected io.kestra.core.models.templates.Template findTemplate(ApplicationContext applicationContext) throws IllegalVariableEvaluationException {
            return this.template;
        }

        public static ExecutorTemplate of(Template templateTask, io.kestra.core.models.templates.Template template) {
            Map<String, Object> map = JacksonMapper.toMap(templateTask);
            map.put("type", ExecutorTemplate.class.getName());

            ExecutorTemplate executorTemplate = JacksonMapper.toMap(map, ExecutorTemplate.class);
            executorTemplate.template = template;

            return executorTemplate;
        }
    }

    public static Flow injectTemplate(Flow flow, Execution execution, BiFunction<String, String, io.kestra.core.models.templates.Template> provider) throws InternalException {
        AtomicReference<Flow> flowReference = new AtomicReference<>(flow);

        boolean haveTemplate = true;
        while (haveTemplate) {
            List<Template> templates = flowReference.get().allTasks()
                .filter(task -> task instanceof Template)
                .map(task -> (Template) task)
                .filter(t -> !(t instanceof ExecutorTemplate))
                .collect(Collectors.toList());

            templates
                .forEach(throwConsumer(templateTask -> {
                    io.kestra.core.models.templates.Template template = provider.apply(
                        templateTask.getNamespace(),
                        templateTask.getTemplateId()
                    );

                    if (template != null) {
                        flowReference.set(
                            flowReference.get().updateTask(
                                templateTask.getId(),
                                ExecutorTemplate.of(templateTask, template)
                            )
                        );
                    } else {
                        throw new InternalException("Unable to find template '" + templateTask.getNamespace() + "." + templateTask.getTemplateId() + "'");
                    }
                }));

            haveTemplate = templates.size() > 0;
        }

        return flowReference.get();
    }

    /**
     * Ugly hack to provide the ApplicationContext on {{@link Template#allChildTasks }} &amp; {{@link Template#tasksTree }}
     * We need to inject a way to fetch Template ...
     */
    @Singleton
    public static class ContextHelper {
        @Inject
        private ApplicationContext applicationContext;

        private static ApplicationContext context;

        public static ApplicationContext context() {
            return ContextHelper.context;
        }

        @EventListener
        void onStartup(final StartupEvent event) {
            ContextHelper.context = this.applicationContext;
        }
    }

    public interface TemplateExecutorInterface {
        Optional<io.kestra.core.models.templates.Template> findById(String namespace, String templateId);
    }

    @TemplateEnabled
    public static class MemoryTemplateExecutor implements io.kestra.core.tasks.flows.Template.TemplateExecutorInterface {
        @Inject
        private TemplateRepositoryInterface templateRepository;

        public Optional<io.kestra.core.models.templates.Template> findById(String namespace, String templateId) {
            return this.templateRepository.findById(namespace, templateId);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The args passed to the template"
        )
        private final Map<String, Object> args;
    }
}
