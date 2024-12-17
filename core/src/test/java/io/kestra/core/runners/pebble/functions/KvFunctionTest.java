package io.kestra.core.runners.pebble.functions;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

@KestraTest(startRunner = true)
public class KvFunctionTest {
    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private QueueInterface<LogEntry> logQueue;

    @BeforeEach
    void reset() throws IOException {
        storageInterface.deleteByPrefix(null, null, URI.create(StorageContext.kvPrefix("io.kestra.tests")));
    }

    @Test
    @LoadFlows({"flows/valids/kv.yaml"})
    void get() throws TimeoutException, IOException, QueueException {
        KVStore kv = new InternalKVStore(null, "io.kestra.tests", storageInterface);
        kv.put("my-key", new KVValueAndMetadata(null, Map.of("field", "value")));

        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "kv");
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("value"), is("value"));
        assertThat(execution.getTaskRunList().get(1).getOutputs().get("value"), is("value"));
    }

    @Test
    @LoadFlows({"flows/valids/kv.yaml"})
    void getKeyNotFound() throws TimeoutException, QueueException {
        Flux<LogEntry> receive = TestsUtils.receive(logQueue);

        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "kv", null, (flow, exec) -> Map.of("errorOnMissing", true));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("value"), is(""));
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        TaskRun taskRun = execution.getTaskRunList().get(1);
        assertThat(taskRun.getState().getCurrent(), is(State.Type.FAILED));

        assertThat(
            receive.toStream()
                .filter(logEntry -> logEntry.getTaskRunId() != null && logEntry.getTaskRunId().equals(taskRun.getId()))
                .anyMatch(log -> log.getMessage().contains("io.pebbletemplates.pebble.error.PebbleException: The key 'my-key' does not exist in the namespace 'io.kestra.tests'. ({{ kv('my-key', inputs.namespace, inputs.errorOnMissing).field }}:1")),
            is(true)
        );
    }
}
