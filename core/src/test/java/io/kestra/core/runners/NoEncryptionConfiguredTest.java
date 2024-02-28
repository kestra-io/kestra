package io.kestra.core.runners;

import io.kestra.core.exceptions.MissingRequiredArgument;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NoEncryptionConfiguredTest extends AbstractMemoryRunnerTest implements TestPropertyProvider {

    @Inject
    private FlowRepositoryInterface flowRepository;

    // this will erase the property from the application-test.yml effectively making encryption not configured
    @Override
    public @NonNull Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("kestra.encryption.secret-key", null);
        return properties;
    }

    @Test
    void encryptedStringOutput() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "encrypted-string");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(2));
        TaskRun hello = execution.findTaskRunsByTaskId("hello").get(0);
        Map<String, String> valueOutput = (Map<String, String>) hello.getOutputs().get("value");
        assertThat(valueOutput.size(), is(2));
        assertThat(valueOutput.get("type"), is(EncryptedString.TYPE));
        // the value is not encrypted as there is no encryption key
        assertThat(valueOutput.get("value"), is("Hello World"));
        TaskRun returnTask = execution.findTaskRunsByTaskId("return").get(0);
        // the output is automatically decrypted so the return has the decrypted value of the hello task output
        assertThat(returnTask.getOutputs().get("value"), is("Hello World"));
    }

    @Test
    void secretInput() {
        Flow flow = flowRepository.findById(null, "io.kestra.tests", "inputs").get();
        Execution execution = Execution.builder()
            .id("test")
            .namespace(flow.getNamespace())
            .flowRevision(1)
            .flowId(flow.getId())
            .build();

        assertThrows(MissingRequiredArgument.class, () -> runnerUtils.typedInputs(flow, execution, InputsTest.inputs));
    }
}
