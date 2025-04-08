package io.kestra.core.models.flows;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class FlowIdTest {

    @Test
    void shouldGetUidWithoutRevision() {
        String id = FlowId.uidWithoutRevision("tenant", "io.kestra.unittest", "flow-id");
        assertThat(id, is("tenant_io.kestra.unittest_flow-id"));
    }

    @Test
    void shouldGetUidGivenEmptyRevision() {
        String id = FlowId.uid("tenant", "io.kestra.unittest", "flow-id", Optional.empty());
        assertThat(id, is("tenant_io.kestra.unittest_flow-id_-1"));
    }

    @Test
    void shouldGetUidGivenRevision() {
        String id = FlowId.uid("tenant", "io.kestra.unittest", "flow-id", Optional.of(42));
        assertThat(id, is("tenant_io.kestra.unittest_flow-id_42"));
    }
}