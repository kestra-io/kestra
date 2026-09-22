package io.kestra.core.models.assets;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AssetTest {
    @Test
    void shouldDeserializeAssetWithoutTypeAsExternal() throws JsonProcessingException {
        Asset asset = JacksonMapper.ofYaml().readValue(
            """
                id: my-asset
                metadata:
                    owner: infra-team""",
            Asset.class
        );

        assertThat(asset).isInstanceOf(External.class);
        assertThat(asset.getType()).isEqualTo(External.ASSET_TYPE);
        assertThat(asset.getId()).isEqualTo("my-asset");
        assertThat(asset.getMetadata()).containsEntry("owner", "infra-team");
    }

    @Test
    void shouldReplaceTypeWhenANewOneIsDeclared() {
        // Given
        Custom previous = Custom.builder()
            .namespace("io.kestra")
            .id("my-asset")
            .type("VM")
            .metadata(Map.of("provider", "aws"))
            .build();
        Custom incoming = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").build();

        // When
        Custom updated = incoming.toUpdated(previous);

        // Then
        assertThat(updated.getType()).isEqualTo("EC2");
        assertThat(updated.getMetadata()).containsEntry("provider", "aws");
    }

    @Test
    void shouldKeepPreviousTypeWhenDeclaredAsExternal() {
        // Given an asset that already has a real type, re-referenced by id alone (deserialized as External)
        Custom previous = Custom.builder().namespace("io.kestra").id("my-asset").type("Custom").build();
        External incoming = External.builder().id("my-asset").build();

        // When
        Asset updated = incoming.toUpdated(previous);

        // Then
        assertThat(updated.getType()).isEqualTo("Custom");
    }

    @Test
    void shouldFallBackToPreviousTypeWhenIncomingTypeIsNull() {
        // Given
        Custom previous = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").build();
        Custom incoming = Custom.builder().namespace("io.kestra").id("my-asset").build();

        // When
        Custom updated = incoming.toUpdated(previous);

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
        Custom updated = incoming.toUpdated(previous);

        // Then
        assertThat(updated.getCreated()).isEqualTo(createdAt);
        assertThat(updated.getUpdated()).isAfter(createdAt);
    }

    @Test
    void shouldKeepIncomingTypeWhenThereIsNoPreviousAsset() {
        // Given
        Custom incoming = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").build();

        // When creating, there is no previous asset to supply a type either way
        Custom created = incoming.toUpdated(null);

        // Then
        assertThat(created.getType()).isEqualTo("EC2");
    }

    @Test
    void shouldKeepPreviousNamespaceWhenNotDeclared() {
        // Given an existing asset referenced by its id alone, so deserialized as External
        Custom previous = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").build();
        External incoming = External.builder().id("my-asset").build();

        // When
        Asset updated = incoming.toUpdated(previous);

        // Then
        assertThat(updated.getNamespace()).isEqualTo("io.kestra");
    }

    @Test
    void shouldKeepPreviousNamespaceWhenNotDeclaredEvenIfIncomingIsNonNull() {
        // Given
        Custom previous = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").build();
        Custom incoming = Custom.builder().namespace("io.kestra.other").id("my-asset").type("EC2").build();

        // When
        Custom updated = incoming.toUpdated(previous, false);

        // Then
        assertThat(updated.getNamespace()).isEqualTo("io.kestra");
    }

    @Test
    void shouldUseDeclaredNamespaceOverPrevious() {
        // Given
        Custom previous = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").build();
        Custom incoming = Custom.builder().namespace("io.kestra.other").id("my-asset").type("EC2").build();

        // When
        Custom updated = incoming.toUpdated(previous, true);

        // Then
        assertThat(updated.getNamespace()).isEqualTo("io.kestra.other");
    }

    @Test
    void shouldUseDeclaredNamespaceOverPreviousViaConvenienceOverload() {
        // Given
        Custom previous = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").build();
        Custom incoming = Custom.builder().namespace("io.kestra.other").id("my-asset").type("EC2").build();

        // When
        Custom updated = incoming.toUpdated(previous);

        // Then
        assertThat(updated.getNamespace()).isEqualTo("io.kestra.other");
    }

    @Test
    void shouldClearNamespaceWhenExplicitlyDeclaredNull() {
        // Given
        Custom previous = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").build();
        Custom incoming = Custom.builder().namespace(null).id("my-asset").type("EC2").build();

        // When
        Custom updated = incoming.toUpdated(previous, true);

        // Then
        assertThat(updated.getNamespace()).isNull();
    }
}
