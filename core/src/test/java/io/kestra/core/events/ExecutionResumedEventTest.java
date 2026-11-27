package io.kestra.core.events;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.FormInput;
import io.kestra.core.models.flows.input.SecretInput;
import io.kestra.core.models.flows.input.StringInput;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionResumedEventTest {

    @Test
    void shouldMaskASecretNestedInAFormInputEvenWhenItsValueIsNotEncrypted() {
        List<Input<?>> definitions = List.of(
            FormInput.builder().id("credentials").type(Type.FORM).inputs(List.of(
                SecretInput.builder().id("token").type(Type.SECRET).build(),
                StringInput.builder().id("user").type(Type.STRING).build()
            )).build()
        );
        Execution execution = Execution.builder().id("execution").tenantId("main").namespace("io.kestra.tests").flowId("flow").build();

        ExecutionResumedEvent event = new ExecutionResumedEvent(execution, execution, null, null, State.Type.RUNNING,
            Map.of("credentials", Map.of("token", "plain-text", "user", "john")), definitions);

        assertThat(event.getResume().inputs()).isEqualTo(Map.of("credentials", Map.of("token", ExecutionResumedEvent.MASK, "user", "john")));
    }
}
