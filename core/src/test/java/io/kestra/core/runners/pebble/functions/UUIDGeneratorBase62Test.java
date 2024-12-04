package io.kestra.core.runners.pebble.functions;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

@KestraTest
class UUIDGeneratorBase62Test {
    @Inject VariableRenderer variableRenderer;

    @Test
    void checkMaxUUIDValue() throws IllegalVariableEvaluationException {
        String rendered =
            variableRenderer.render(
                "{{ generateUUID() }}", Collections.emptyMap());
        assertThat(rendered, rendered.length() <=22);
    }
}
