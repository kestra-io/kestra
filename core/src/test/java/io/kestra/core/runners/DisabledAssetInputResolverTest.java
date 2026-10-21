package io.kestra.core.runners;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.AssetInput;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisabledAssetInputResolverTest {
    @Test
    void erroringOnOpenSource() {
        AssetInput input = AssetInput.builder().id("vm").type(Type.ASSET).build();

        assertThatThrownBy(() -> new DisabledAssetInputResolver().resolve(Execution.builder().build(), input, "web-01"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Enterprise Edition");
    }
}
