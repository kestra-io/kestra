package io.kestra.core.tasks.flows;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.GraphService;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Include a resuable template inside a flow"
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
public class Template extends Task implements FlowableTask<Template.Output> {
    @Valid
    protected List<Task> errors;

    @NotNull
    @Schema(
        title = "The namespace of the template"
    )
    @PluginProperty(dynamic = false)
    private String namespace;

    @NotNull
    @Schema(
        title = "The id of the template"
    )
    @PluginProperty(dynamic = false)
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
        io.kestra.core.models.templates.Template template = this.findTemplate(ContextHelper.context());

        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.SEQUENTIAL);

        GraphService.sequential(
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
        io.kestra.core.models.templates.Template template = this.findTemplate(runContext.getApplicationContext());

        return FlowableUtils.resolveTasks(template.getTasks(), parentTaskRun);
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        io.kestra.core.models.templates.Template template = this.findTemplate(runContext.getApplicationContext());

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

    private io.kestra.core.models.templates.Template findTemplate(ApplicationContext applicationContext) throws IllegalVariableEvaluationException {
        TemplateExecutorInterface templateExecutor = applicationContext.getBean(TemplateExecutorInterface.class);

        io.kestra.core.models.templates.Template template = templateExecutor.findById(this.namespace, this.templateId);
        if (template == null) {
            throw new IllegalVariableEvaluationException("Can't find flow template '" + this.namespace + "." + this.templateId + "'");
        }

        return template;
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
        io.kestra.core.models.templates.Template findById(String namespace, String templateId);
    }

    public static class MemoryTemplateExecutor implements io.kestra.core.tasks.flows.Template.TemplateExecutorInterface {
        @Inject
        private TemplateRepositoryInterface templateRepository;

        public io.kestra.core.models.templates.Template findById(String namespace, String templateId) {
            return this.templateRepository.findById(namespace, templateId).orElse(null);
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
