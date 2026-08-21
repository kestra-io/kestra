package io.kestra.core.models.assets;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssetTest {
    @Test
    void toUpdatedShouldKeepPreviousTypeWhenTypeChangeIsNotAllowed() {
        Custom previous = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").build();
        Custom incoming = Custom.builder().namespace("io.kestra").id("my-asset").type("VM").build();

        assertThat(incoming.toUpdated(previous, false).getType()).isEqualTo("EC2");
    }

    @Test
    void toUpdatedShouldReplaceTypeWhenTypeChangeIsAllowed() {
        Custom previous = Custom.builder()
            .namespace("io.kestra")
            .id("my-asset")
            .type("VM")
            .metadata(Map.of("provider", "aws"))
            .build();
        Custom incoming = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").build();

        Custom updated = incoming.toUpdated(previous, true);

        assertThat(updated.getType()).isEqualTo("EC2");
        assertThat(updated.getMetadata()).containsEntry("provider", "aws");
    }

    @Test
    void toUpdatedShouldFallBackToPreviousTypeWhenIncomingHasNone() {
        Custom previous = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").build();
        Custom incoming = Custom.builder().namespace("io.kestra").id("my-asset").build();

        assertThat(incoming.toUpdated(previous, true).getType()).isEqualTo("EC2");
    }
}
