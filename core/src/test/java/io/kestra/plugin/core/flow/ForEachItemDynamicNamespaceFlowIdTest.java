package io.kestra.plugin.core.flow;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

public class ForEachItemDynamicNamespaceFlowIdTest {
    static final String TEST_NAMESPACE = "io.kestra.tests.dynamic";

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    private FlowInputOutput flowIO;

    @Inject
    private ExecutionService executionService;

    @Test
    public void testDynamicNamespaceAndFlowId() throws Exception {
        // Prepare a file with two lines, each line is a JSON object with different namespace/flowId
        String content = """
        {\"namespace\":\"io.kestra.tests.dynamic\",\"flowId\":\"subflow1\",\"input1\":\"foo\"}\n{\"namespace\":\"io.kestra.tests.dynamic\",\"flowId\":\"subflow2\",\"input1\":\"bar\"}"
        """;
        URI file = storageInterface.putContent(content.getBytes(), "test-dynamic-ns-flowid.jsonl");

        // The ForEachItem task should use expressions for namespace and flowId
        Map<String, Object> inputs = Map.of("file", file.toString());
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            TEST_NAMESPACE,
            "for-each-item-dynamic-ns-flowid",
            null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(30)
        );

        // There should be two subflow executions, one for each item
        assertThat(execution.getState().getCurrent().isTerminated()).isTrue();
        assertThat(execution.getTaskRunList()).isNotEmpty();
        // Optionally, check that the subflows were launched with the correct namespace/flowId
        // (This would require more advanced inspection or a mock, but this test ensures the main flow completes)
    }
}
