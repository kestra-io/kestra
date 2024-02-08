package io.kestra.core.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
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
            .filter(r -> r.getTaskRunId() != null && r.getTaskRunId().equals(taskRun.getId()))
            .collect(Collectors.toList());
    }

    public static LogEntry awaitLog(List<LogEntry> logs, Predicate<LogEntry> logMatcher) {
        List<LogEntry> matchingLogs = awaitLogs(logs, logMatcher, (Predicate<Integer>) null);
        return matchingLogs.isEmpty() ? null : matchingLogs.get(0);
    }

    public static List<LogEntry> awaitLogs(List<LogEntry> logs, Integer exactCount) {
        return awaitLogs(logs, logEntry -> true, exactCount::equals);
    }

    public static List<LogEntry> awaitLogs(List<LogEntry> logs, Predicate<LogEntry> logMatcher, Integer exactCount) {
        return awaitLogs(logs, logMatcher, exactCount::equals);
    }

    public static List<LogEntry> awaitLogs(List<LogEntry> logs, Predicate<LogEntry> logMatcher, Predicate<Integer> countMatcher) {
        AtomicReference<List<LogEntry>> matchingLogs = new AtomicReference<>();
        try {
            Await.until(() -> {
                matchingLogs.set(
                    Collections.synchronizedList(logs)
                        .stream()
                        .filter(logMatcher)
                        .collect(Collectors.toList())
                );

                if(countMatcher == null){
                    return !matchingLogs.get().isEmpty();
                }

                int matchingLogsCount = matchingLogs.get().size();
                return countMatcher.test(matchingLogsCount);
            }, Duration.ofMillis(10), Duration.ofMillis(500));
        } catch (TimeoutException e) {}

        return matchingLogs.get();
    }

    public static Flow mockFlow() {
        return TestsUtils.mockFlow(Thread.currentThread().getStackTrace()[2]);
    }

    private static Flow mockFlow(StackTraceElement caller) {
        return Flow.builder()
            .namespace(caller.getClassName().toLowerCase())
            .id(caller.getMethodName().toLowerCase())
            .revision(1)
            .build();
    }

    public static Execution mockExecution(Flow flow, Map<String, Object> inputs) {
        return TestsUtils.mockExecution(Thread.currentThread().getStackTrace()[2], flow, inputs);
    }

    private static Execution mockExecution(StackTraceElement caller, Flow flow, Map<String, Object> inputs) {
        return Execution.builder()
            .id(IdUtils.create())
            .tenantId(flow.getTenantId())
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

    public static Map.Entry<ConditionContext, TriggerContext> mockTrigger(RunContextFactory runContextFactory, AbstractTrigger trigger) {
        StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
        Flow flow = TestsUtils.mockFlow(caller);

        TriggerContext triggerContext = TriggerContext.builder()
            .triggerId(trigger.getId())
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .flowRevision(flow.getRevision())
            .date(ZonedDateTime.now())
            .build();

        return new AbstractMap.SimpleEntry<>(
            ConditionContext.builder()
                .runContext(runContextFactory.of(flow, trigger).forScheduler(triggerContext, trigger))
                .flow(flow)
                .build(),
            triggerContext
        );
    }

    public static RunContext mockRunContext(RunContextFactory runContextFactory, Task task, Map<String, Object> inputs) {
        StackTraceElement caller = Thread.currentThread().getStackTrace()[2];

        Flow flow = TestsUtils.mockFlow(caller);
        Execution execution = TestsUtils.mockExecution(caller, flow, inputs);
        TaskRun taskRun = TestsUtils.mockTaskRun(caller, execution, task);

        return runContextFactory.of(flow, task, execution, taskRun);
    }
}
