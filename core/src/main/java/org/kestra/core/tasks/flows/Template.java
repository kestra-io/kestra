package org.kestra.core.tasks.flows;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.exceptions.InvalidFlowStateException;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.hierarchies.ParentTaskTree;
import org.kestra.core.models.hierarchies.TaskTree;
import org.kestra.core.models.tasks.FlowableTask;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.tasks.VoidOutput;
import org.kestra.core.repositories.TemplateRepositoryInterface;
import org.kestra.core.runners.FlowableUtils;
import org.kestra.core.runners.RunContext;
import org.kestra.core.services.TreeService;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.Valid;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Documentation(
    description = "Process tasks ones after others sequentially",
    body = "Mostly use in order to group tasks."
)
@Example(
    full = true,
    code = {
        "id: template",
        "namespace: org.kestra.tests",
        "",
        "tasks:",
        "  - id: template",
        "    type: org.kestra.core.tasks.flows.Template",
        "    tasks:",
        "      - id: 1st",
        "        type: org.kestra.core.tasks.debugs.Return",
        "        format: \"{{task.id}} > {{taskrun.startDate}}\"",
        "      - id: 2nd",
        "        type: org.kestra.core.tasks.debugs.Return",
        "        format: \"{{task.id}} > {{taskrun.id}}\"",
        "  - id: last",
        "    type: org.kestra.core.tasks.debugs.Return",
        "    format: \"{{task.id}} > {{taskrun.startDate}}\""
    }
)
public class Template extends Task implements FlowableTask<VoidOutput> {

    @Valid
    protected List<Task> errors;

    @Valid
    private List<Task> tasks;

    @Valid
    private String templateId;

    @Valid
    private String namespace;

    @Override
    public List<TaskTree> tasksTree(String parentId, Execution execution, List<String> groups) throws IllegalVariableEvaluationException {
        org.kestra.core.models.templates.Template template = this.findTemplate(ContextHelper.context());

        return TreeService.sequential(
            template.getTasks(),
            template.getErrors(),
            Collections.singletonList(ParentTaskTree.builder()
                .id(getId())
                .build()
            ),
            execution,
            groups
        );
    }

    @Override
    public List<Task> allChildTasks() {
        org.kestra.core.models.templates.Template template = this.findTemplate(ContextHelper.context());

        return Stream
            .concat(
                template.getTasks() != null ? template.getTasks().stream() : Stream.empty(),
                template.getErrors() != null ? template.getErrors().stream() : Stream.empty()
            )
            .collect(Collectors.toList());
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        org.kestra.core.models.templates.Template template = this.findTemplate(runContext.getApplicationContext());

        return FlowableUtils.resolveTasks(template.getTasks(), parentTaskRun);
    }

    @Override
    public List<TaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        org.kestra.core.models.templates.Template template = this.findTemplate(runContext.getApplicationContext());

        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(template.getErrors(), parentTaskRun),
            parentTaskRun
        );
    }

    private org.kestra.core.models.templates.Template findTemplate(ApplicationContext applicationContext) {
        TemplateRepositoryInterface templateRepository = applicationContext.getBean(TemplateRepositoryInterface.class);

        return templateRepository
            .findById(
                this.namespace,
                this.templateId
            )
            .orElseThrow(() -> new InvalidFlowStateException("Can't find flow template '" + this.namespace + "." + this.templateId + "'"));
    }

    @Singleton
    public static class ContextHelper {
        @Inject
        private ApplicationContext applicationContext;

        private static ApplicationContext context;

        public static ApplicationContext context() {
            return ContextHelper.context;
        }

        @EventListener
        void onStartup(final StartupEvent event) throws IOException {
            ContextHelper.context = this.applicationContext;
        }
    }
}
