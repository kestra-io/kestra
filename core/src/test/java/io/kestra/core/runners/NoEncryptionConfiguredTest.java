package io.kestra.core.runners;

import io.kestra.core.exceptions.InputOutputValidationException;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.micronaut.core.annotation.NonNull;
import io.kestra.core.junit.annotations.KestraTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.Map;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest(startRunner = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NoEncryptionConfiguredTest implements TestPropertyProvider {
    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private FlowInputOutput flowIO;

    // this will erase the property from the application-test.yml effectively making encryption not configured
    @Override
    public @NonNull Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("kestra.encryption.secret-key", null);
        return properties;
    }

    @SuppressWarnings("unchecked")
    @Test
    @ExecuteFlow("flows/valids/encrypted-string.yaml")
    void encryptedStringOutput(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(2);
        TaskRun hello = execution.findTaskRunsByTaskId("hello").getFirst();
        Map<String, String> valueOutput = (Map<String, String>) hello.getOutputs().get("value");
        assertThat(valueOutput.size()).isEqualTo(2);
        assertThat(valueOutput.get("type")).isEqualTo(EncryptedString.TYPE);
        // the value is not encrypted as there is no encryption key
        assertThat(valueOutput.get("value")).isEqualTo("Hello World");
        TaskRun returnTask = execution.findTaskRunsByTaskId("return").getFirst();
        // the output is automatically decrypted so the return has the decrypted value of the hello task output
        assertThat(returnTask.getOutputs().get("value")).isEqualTo("Hello World");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void secretInput() {
        assertThat(flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "inputs").isPresent()).isTrue();

        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "inputs").get();
        Execution execution = Execution.builder()
            .id("test")
            .namespace(flow.getNamespace())
            .tenantId(MAIN_TENANT)
            .flowRevision(1)
            .flowId(flow.getId())
            .build();

        assertThrows(InputOutputValidationException.class, () -> flowIO.readExecutionInputs(flow, execution, InputsTest.inputs));
    }
}
