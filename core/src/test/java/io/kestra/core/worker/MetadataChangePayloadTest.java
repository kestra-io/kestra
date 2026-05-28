package io.kestra.core.worker;

import io.kestra.core.serializers.JacksonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataChangePayloadTest {

    @Test
    void shouldRoundTripPayloadWithNullableNamespace() throws Exception {
        // Given — a TENANT payload exercises namespace == null; NAMESPACE / FLOW
        // exercise the non-null path; the record is otherwise plain Jackson.
        for (MetadataChangePayload payload : new MetadataChangePayload[]{
            new MetadataChangePayload(MetadataChangePayload.Type.NAMESPACE, "tenant-a", "prod.team"),
            new MetadataChangePayload(MetadataChangePayload.Type.TENANT,    "tenant-a", null),
            new MetadataChangePayload(MetadataChangePayload.Type.FLOW,      "tenant-a", "prod.team")
        }) {
            // When
            String json = JacksonMapper.ofJson().writeValueAsString(payload);
            MetadataChangePayload result = JacksonMapper.ofJson().readValue(json, MetadataChangePayload.class);

            // Then
            assertThat(result).isEqualTo(payload);
        }
    }
}
