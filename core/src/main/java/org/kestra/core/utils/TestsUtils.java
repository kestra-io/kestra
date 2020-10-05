package org.kestra.core.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.repositories.LocalFlowRepositoryLoader;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunContextFactory;
import org.kestra.core.serializers.JacksonMapper;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

abstract public class TestsUtils {
    private static final ObjectMapper mapper = JacksonMapper.ofYaml();

    public static <T> T map(String path, Class<T> cls) throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        String read = Files.asCharSource(new File(resource.getFile()), Charsets.UTF_8).read();

        return mapper.readValue(read, cls);
    }

    public static void loads(LocalFlowRepositoryLoader repositoryLoader) throws IOException, URISyntaxException {
        TestsUtils.loads(repositoryLoader, Objects.requireNonNull(TestsUtils.class.getClassLoader().getResource("flows/valids")));
    }

    public static void loads(LocalFlowRepositoryLoader repositoryLoader, URL url) throws IOException, URISyntaxException {
        repositoryLoader.load(url);
    }

    public static List<LogEntry> filterLogs(List<LogEntry> logs, TaskRun taskRun) {
        return logs
            .stream()
            .filter(r -> r.getTaskRunId().equals(taskRun.getId()))
            .collect(Collectors.toList());
    }

    public static Flow mockFlow() {
        return TestsUtils.mockFlow(Thread.currentThread().getStackTrace()[2]);
    }

    private static Flow mockFlow(StackTraceElement caller) {
        return Flow.builder()
            .namespace(caller.getClassName())
            .id(caller.getMethodName())
            .revision(1)
            .build();
    }

    public static Execution mockExecution(Flow flow, Map<String, Object> inputs) {
        return TestsUtils.mockExecution(Thread.currentThread().getStackTrace()[2], flow, inputs);
    }

    private static Execution mockExecution(StackTraceElement caller, Flow flow, Map<String, Object> inputs) {
        return Execution.builder()
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .inputs(inputs)
            .state(new State())
            .build()
            .withState(State.Type.RUNNING);
    }

    public static TaskRun mockTaskRun(Flow flow, Execution execution, Task task) {
        return TestsUtils.mockTaskRun(Thread.currentThread().getStackTrace()[2], execution, task);
    }

    private static TaskRun mockTaskRun(StackTraceElement caller, Execution execution, Task task) {
        return TaskRun.builder()
            .id(IdUtils.create())
            .executionId(execution.getId())
            .namespace(execution.getNamespace())
            .flowId(execution.getFlowId())
            .taskId(task.getId())
            .state(new State())
            .build()
            .withState(State.Type.RUNNING);
    }

    public static RunContext mockRunContext(RunContextFactory runContextFactory, Task task, Map<String, Object> inputs) {
        StackTraceElement caller = Thread.currentThread().getStackTrace()[2];

        Flow flow = TestsUtils.mockFlow(caller);
        Execution execution = TestsUtils.mockExecution(caller, flow, inputs);
        TaskRun taskRun = TestsUtils.mockTaskRun(caller, execution, task);

        return runContextFactory.of(flow, task, execution, taskRun);
    }
}
