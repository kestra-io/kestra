package io.kestra.core.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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
            .toList();
    }

    public static LogEntry awaitLog(List<LogEntry> logs, Predicate<LogEntry> logMatcher) {
        List<LogEntry> matchingLogs = awaitLogs(logs, logMatcher, (Predicate<Integer>) null);
        return matchingLogs.isEmpty() ? null : matchingLogs.getFirst();
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
        return TestsUtils.mockExecution(Thread.currentThread().getStackTrace()[2], flow, inputs, null);
    }

    public static Execution mockExecution(Flow flow, Map<String, Object> inputs, Map<String, Object> outputs) {
        return TestsUtils.mockExecution(Thread.currentThread().getStackTrace()[2], flow, inputs, outputs);
    }

    private static Execution mockExecution(StackTraceElement caller,
                                           Flow flow,
                                           Map<String, Object> inputs,
                                           Map<String, Object> outputs) {
        return Execution.builder()
            .id(IdUtils.create())
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .inputs(inputs)
            .state(new State())
            .outputs(outputs)
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
                .runContext(runContextFactory.initializer().forScheduler((DefaultRunContext) runContextFactory.of(flow, trigger), triggerContext, trigger))
                .flow(flow)
                .build(),
            triggerContext
        );
    }

    public static RunContext mockRunContext(RunContextFactory runContextFactory, Task task, Map<String, Object> inputs) {
        StackTraceElement caller = Thread.currentThread().getStackTrace()[2];

        Flow flow = TestsUtils.mockFlow(caller);
        Execution execution = TestsUtils.mockExecution(caller, flow, inputs, null);
        TaskRun taskRun = TestsUtils.mockTaskRun(caller, execution, task);

        return runContextFactory.of(flow, task, execution, taskRun);
    }

    public static <T> Flux<T> receive(QueueInterface<T> queue) {
        return TestsUtils.receive(queue, null);
    }

    public static <T> Flux<T> receive(QueueInterface<T> queue, Consumer<Either<T, DeserializationException>> consumer) {
        return TestsUtils.receive(queue, null, null, consumer, null);
    }

    public static <T> Flux<T> receive(QueueInterface<T> queue, Class<?> queueType, Consumer<Either<T, DeserializationException>> consumer) {
        return TestsUtils.receive(queue, null, queueType, consumer, null);
    }

    public static <T> Flux<T> receive(QueueInterface<T> queue, String consumerGroup, Class<?> queueType, Consumer<Either<T, DeserializationException>> consumer) {
        return TestsUtils.receive(queue, consumerGroup, queueType, consumer, null);
    }

    public static <T> Flux<T> receive(QueueInterface<T> queue, String consumerGroup, Consumer<Either<T, DeserializationException>> consumer) {
        return TestsUtils.receive(queue, consumerGroup, null, consumer, null);
    }

    public static <T> Flux<T> receive(QueueInterface<T> queue, String consumerGroup, Class<?> queueType, Consumer<Either<T, DeserializationException>> consumer, Duration timeout) {
        List<T> elements = new CopyOnWriteArrayList<>();
        AtomicReference<DeserializationException> exceptionRef = new AtomicReference<>();
        Consumer<Either<T, DeserializationException>> eitherConsumer = (either) -> {
            if (either.isLeft()) {
                elements.add(either.getLeft());
            } else {
                exceptionRef.set(either.getRight());
            }

            if (consumer != null) {
                consumer.accept(either);
            }
        };
        Runnable receiveCancellation = queueType == null ? queue.receive(consumerGroup, eitherConsumer, false) : queue.receive(consumerGroup, queueType, eitherConsumer, false);

        AtomicBoolean isCancelled = new AtomicBoolean(false);
        Flux<T> flux = Flux.<T>create(sink -> {
            DeserializationException exception = exceptionRef.get();
            if (exception == null) {
                elements.forEach(sink::next);
                sink.complete();
            } else {
                sink.error(exception);
            }
        }).doFinally(signalType -> {
            isCancelled.set(true);
            receiveCancellation.run();
        });

        new Thread(() -> {
            try {
                Await.until(isCancelled::get, null, Optional.ofNullable(timeout).orElse(Duration.ofMinutes(1)));
            } catch (TimeoutException e) {
                // If the receive hasn't been stopped after the given timeout (which means no subscription was done), we stop it
                receiveCancellation.run();
            }
        }).start();

        return flux;
    }
}
