package io.kestra.core.models.tasks;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.kestra.core.models.flows.State;

import static org.assertj.core.api.Assertions.assertThat;

class AssetFailureBehaviorTest {

    @ParameterizedTest
    @MethodSource("cases")
    void apply(AssetFailureBehavior behavior, State.Type current, State.Type expected) {
        assertThat(behavior.apply(current)).isEqualTo(expected);
    }

    private static Stream<Arguments> cases() {
        return Stream.of(
            // IGNORE never changes anything
            Arguments.of(AssetFailureBehavior.IGNORE, State.Type.SUCCESS, State.Type.SUCCESS),
            Arguments.of(AssetFailureBehavior.IGNORE, State.Type.WARNING, State.Type.WARNING),
            Arguments.of(AssetFailureBehavior.IGNORE, State.Type.FAILED, State.Type.FAILED),
            Arguments.of(AssetFailureBehavior.IGNORE, State.Type.KILLED, State.Type.KILLED),
            Arguments.of(AssetFailureBehavior.IGNORE, State.Type.CANCELLED, State.Type.CANCELLED),

            // WARN escalates SUCCESS only; a terminated-in-error state is never touched
            Arguments.of(AssetFailureBehavior.WARN, State.Type.SUCCESS, State.Type.WARNING),
            Arguments.of(AssetFailureBehavior.WARN, State.Type.WARNING, State.Type.WARNING),
            Arguments.of(AssetFailureBehavior.WARN, State.Type.FAILED, State.Type.FAILED),
            Arguments.of(AssetFailureBehavior.WARN, State.Type.KILLED, State.Type.KILLED),
            Arguments.of(AssetFailureBehavior.WARN, State.Type.CANCELLED, State.Type.CANCELLED),

            // FAIL always fails, except a state that already terminated in error
            Arguments.of(AssetFailureBehavior.FAIL, State.Type.SUCCESS, State.Type.FAILED),
            Arguments.of(AssetFailureBehavior.FAIL, State.Type.WARNING, State.Type.FAILED),
            Arguments.of(AssetFailureBehavior.FAIL, State.Type.FAILED, State.Type.FAILED),
            Arguments.of(AssetFailureBehavior.FAIL, State.Type.KILLED, State.Type.KILLED),
            Arguments.of(AssetFailureBehavior.FAIL, State.Type.CANCELLED, State.Type.CANCELLED)
        );
    }
}
