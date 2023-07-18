package io.kestra.core.tasks.flows;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.validations.WorkingDirectoryTaskValidation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Collections;
import java.util.List;

@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run tasks sequentially in the same working directory",
    description = "Tasks are stateless by default. Kestra will launch each task within a temporary working directory on a Worker. " +
        "The `WorkingDirectory` task allows reusing the same file system's working directory across multiple tasks " +
        "so that multiple sequential tasks can use output files from previous tasks without having to use the `outputs.taskId.outputName` syntax. " +
        "Note that the `WorkingDirectory` only works with runnable tasks because those tasks are executed directly on the Worker. " +
        "This means that using flowable tasks such as the `Parallel` task within the `WorkingDirectory` task will not work. " +
        "The `WorkingDirectory` task requires Kestra>=0.9.0."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "Clone a git repository into the Working Directory and run a Python script",
            code = {
                "id: gitPython",
                "namespace: dev",
                "",
                "tasks:",
                "  - id: wdir",
                "    type: io.kestra.core.tasks.flows.WorkingDirectory",
                "    tasks:",
                "      - id: cloneRepository",
                "        type: io.kestra.plugin.git.Clone",
                "        url: https://github.com/kestra-io/examples",
                "        branch: main",
                "      - id: python",
                "        type: io.kestra.plugin.scripts.python.Commands",
                "        docker:",
                "          image: ghcr.io/kestra-io/pydata:latest",
                "        commands:",
                "          - python scripts/etl_script.py"
            }
        ),
        @Example(
            full = true,
            title = "Add input and output files within a Working Directory to use them in a Python script",
            code = """
    id: apiJSONtoMongoDB
    namespace: dev

    tasks:
    - id: wdir
        type: io.kestra.core.tasks.flows.WorkingDirectory
        tasks:
        - id: demoSQL
            type: io.kestra.core.tasks.storages.LocalFiles
            inputs: 
            query.sql: |
                SELECT sum(total) as total, avg(quantity) as avg_quantity
                FROM sales;

        - id: inlineScript
            type: io.kestra.plugin.scripts.python.Script
            runner: DOCKER
            docker:
            image: python:3.11-slim
            beforeCommands:
            - pip install requests kestra > /dev/null
            warningOnStdErr: false
            script: |
            import requests
            import json
            from kestra import Kestra

            with open('query.sql', 'r') as input_file:
                sql = input_file.read()

            response = requests.get('https://api.github.com')
            data = response.json()

            with open('output.json', 'w') as output_file:
                json.dump(data, output_file)
            
            Kestra.outputs({'receivedSQL': sql, 'status': response.status_code})

        - id: jsonFiles
            type: io.kestra.core.tasks.storages.LocalFiles
            outputs:
            - output.json

    - id: loadToMongoDB
        type: io.kestra.plugin.mongodb.Load
        connection:
        uri: mongodb://host.docker.internal:27017/
        database: local
        collection: github
        from: "{{outputs.jsonFiles.uris['output.json']}}"
                """
        ),
        @Example(
            full = true,
            code = {
                "id: working-directory",
                "namespace: io.kestra.tests",
                "",
                "tasks:",
                "  - id: working-directory",
                "    type: io.kestra.core.tasks.flows.WorkingDirectory",
                "    tasks:",
                "      - id: first",
                "        type: io.kestra.core.tasks.scripts.Bash",
                "        commands:",
                "        - 'echo \"{{ taskrun.id }}\" > {{ workingDir }}/stay.txt'",
                "      - id: second",
                "        type: io.kestra.core.tasks.scripts.Bash",
                "        commands:",
                "        - |",
                "          echo '::{\"outputs\": {\"stay\":\"'$(cat {{ workingDir }}/stay.txt)'\"}}::'"
            }
        )
    }
)
@WorkingDirectoryTaskValidation
public class WorkingDirectory extends Sequential {

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        List<ResolvedTask> childTasks = this.childTasks(runContext, parentTaskRun);

        if (execution.hasFailed(childTasks, parentTaskRun)) {
            return super.resolveNexts(runContext, execution, parentTaskRun);
        }

        // resolve to no next tasks as the worker will execute all tasks
        return Collections.emptyList();
    }

    public WorkerTask workerTask(TaskRun parent, Task task, RunContext runContext) {
        return WorkerTask.builder()
            .task(task)
            .taskRun(TaskRun.builder()
                .id(IdUtils.create())
                .executionId(parent.getExecutionId())
                .namespace(parent.getNamespace())
                .flowId(parent.getFlowId())
                .taskId(task.getId())
                .parentTaskRunId(parent.getId())
                .state(new State())
                .build()
            )
            .runContext(runContext)
            .build();
    }
}
