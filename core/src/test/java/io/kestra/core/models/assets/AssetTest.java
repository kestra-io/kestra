package io.kestra.core.models.assets;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssetTest {
    @Test
    void shouldKeepPreviousTypeWhenTypeChangeIsNotAllowed() {
        // Given
        Custom previous = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").build();
        Custom incoming = Custom.builder().namespace("io.kestra").id("my-asset").type("VM").build();

        // When
        Custom updated = incoming.toUpdated(previous, false);

        // Then
        assertThat(updated.getType()).isEqualTo("EC2");
    }

    @Test
    void shouldReplaceTypeWhenTypeChangeIsAllowed() {
        // Given
        Custom previous = Custom.builder()
            .namespace("io.kestra")
            .id("my-asset")
            .type("VM")
            .metadata(Map.of("provider", "aws"))
            .build();
        Custom incoming = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").build();

        // When
        Custom updated = incoming.toUpdated(previous, true);

        // Then
        assertThat(updated.getType()).isEqualTo("EC2");
        assertThat(updated.getMetadata()).containsEntry("provider", "aws");
    }

    @Test
    void shouldFallBackToPreviousTypeWhenIncomingTypeIsNull() {
        // Given
        Custom previous = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").build();
        Custom incoming = Custom.builder().namespace("io.kestra").id("my-asset").build();

        // When
        Custom updated = incoming.toUpdated(previous, true);

        // Then
        assertThat(updated.getType()).isEqualTo("EC2");
    }

    @Test
    void shouldKeepCreationDateWhenUpdating() {
        // Given
        Instant createdAt = Instant.now().minus(3, ChronoUnit.DAYS);
        Custom previous = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").created(createdAt).updated(createdAt).build();
        Custom incoming = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").build();

        // When
        Custom updated = incoming.toUpdated(previous, false);

        // Then
        assertThat(updated.getCreated()).isEqualTo(createdAt);
        assertThat(updated.getUpdated()).isAfter(createdAt);
    }

    @Test
    void shouldKeepIncomingTypeWhenThereIsNoPreviousAsset() {
        // Given
        Custom incoming = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").build();

        // When creating, no previous asset can supply a type whatever the flag
        Custom created = incoming.toUpdated(null, false);

        // Then
        assertThat(created.getType()).isEqualTo("EC2");
    }
}
