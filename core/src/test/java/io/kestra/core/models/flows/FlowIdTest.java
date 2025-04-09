package io.kestra.core.models.flows;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FlowIdTest {

    @Test
    void shouldGetUidWithoutRevision() {
        String id = FlowId.uidWithoutRevision("tenant", "io.kestra.unittest", "flow-id");
        assertThat(id).isEqualTo("tenant_io.kestra.unittest_flow-id");
    }

    @Test
    void shouldGetUidGivenEmptyRevision() {
        String id = FlowId.uid("tenant", "io.kestra.unittest", "flow-id", Optional.empty());
        assertThat(id).isEqualTo("tenant_io.kestra.unittest_flow-id_-1");
    }

    @Test
    void shouldGetUidGivenRevision() {
        String id = FlowId.uid("tenant", "io.kestra.unittest", "flow-id", Optional.of(42));
        assertThat(id).isEqualTo("tenant_io.kestra.unittest_flow-id_42");
    }
}