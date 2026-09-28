package io.kestra.worker.stores;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.micronaut.context.condition.ConditionContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KVStoreFromControllerConditionTest {

    private final KVStoreFromControllerCondition condition = new KVStoreFromControllerCondition();

    @Test
    void shouldMatchWhenAWorkerIsAskedToGoThroughTheController() {
        assertThat(condition.matches(context("WORKER", "CONTROLLER"))).isTrue();
        assertThat(condition.matches(context("WORKER", "controller"))).isTrue();
    }

    @Test
    void shouldNotMatchWhenAWorkerUsesTheInternalStorageItself() {
        assertThat(condition.matches(context("WORKER", null))).isFalse();
        assertThat(condition.matches(context("WORKER", "STORAGE"))).isFalse();
    }

    @Test
    void shouldNotMatchOnEveryOtherServerType() {
        assertThat(condition.matches(context("STANDALONE", "CONTROLLER"))).isFalse();
        assertThat(condition.matches(context("CONTROLLER", "CONTROLLER"))).isFalse();
        assertThat(condition.matches(context(null, "CONTROLLER"))).isFalse();
    }

    @Test
    void shouldFailWhenTheConfiguredModeIsNotAKnownOne() {
        assertThatThrownBy(() -> condition.matches(context("WORKER", "CONTROLER")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CONTROLER");
    }

    @SuppressWarnings("unchecked")
    private static ConditionContext<?> context(String serverType, String workerAccess) {
        ConditionContext<?> context = mock(ConditionContext.class);
        when(context.get("kestra.server-type", String.class)).thenReturn(Optional.ofNullable(serverType));
        when(context.get(KVWorkerAccess.CONFIG_KEY, String.class)).thenReturn(Optional.ofNullable(workerAccess));
        return context;
    }
}
