package org.floworc.core.runners;

import com.google.common.collect.ImmutableMap;
import org.floworc.core.AbstractMemoryRunnerTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputsTest extends AbstractMemoryRunnerTest {
    private Map<String, String> inputs = ImmutableMap.of(
        "string", "myString",
        "int", "42",
        "float", "42.42",
        "instant", "2019-10-06T18:27:49Z"
    );

    @Test
    void missingRequired() {
        assertThrows(IllegalArgumentException.class, () -> {
            runnerUtils.typedInputs("inputs", new HashMap<>());
        });
    }

    @Test
    void inputString() {
        Map<String, Object> typeds = runnerUtils.typedInputs("inputs", this.inputs);
        assertThat(typeds.get("string"), is("myString"));
    }

    @Test
    void inputInt() {
        Map<String, Object> typeds = runnerUtils.typedInputs("inputs", this.inputs);
        assertThat(typeds.get("int"), is(42));
    }

    @Test
    void inputFloat() {
        Map<String, Object> typeds = runnerUtils.typedInputs("inputs", this.inputs);
        assertThat(typeds.get("float"), is(42.42F));
    }

    @Test
    void inputInstant() {
        Map<String, Object> typeds = runnerUtils.typedInputs("inputs", this.inputs);
        assertThat(typeds.get("instant"), is(Instant.parse("2019-10-06T18:27:49Z")));
    }
}