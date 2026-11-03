package io.kestra.core.models.assets;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

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

    @Test
    void shouldDeleteAMetadataKeyWithAnExplicitNullOnCreation() {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("keep", "value");
        metadata.put("drop", null);
        Custom incoming = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").metadata(metadata).build();

        // When
        Custom created = incoming.toUpdated(null);

        // Then
        assertThat(created.getMetadata()).containsEntry("keep", "value");
        assertThat(created.getMetadata()).doesNotContainKey("drop");
    }

    @Test
    void shouldKeepAMetadataKeySetToAnEmptyString() {
        // Given
        Custom previous = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2")
            .metadata(Map.of(Asset.TTL_METADATA_KEY, "2026-01-01T00:00:00.000Z")).build();
        Custom incoming = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2")
            .metadata(Map.of(Asset.TTL_METADATA_KEY, "")).build();

        // When
        Custom updated = incoming.toUpdated(previous);

        // Then
        assertThat(updated.getMetadata()).containsEntry(Asset.TTL_METADATA_KEY, "");
    }

    @Test
    void shouldNotDeleteANestedMetadataKeyWithANull() {
        // Given
        Map<String, Object> nested = new HashMap<>();
        nested.put("x", null);
        Custom previous = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2")
            .metadata(Map.of("m", Map.of("x", 1, "y", 2))).build();
        Custom incoming = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2")
            .metadata(Map.of("m", nested)).build();

        // When
        Custom updated = incoming.toUpdated(previous);

        // Then
        assertThat(updated.getMetadata()).extracting("m").isEqualTo(Map.of("x", 1, "y", 2));
    }

    @Test
    void shouldReadTheSystemPropertiesDeclaredAtRootLevel() throws JsonProcessingException {
        Asset asset = JacksonMapper.ofYaml().readValue(
            """
                id: my-asset
                type: EC2
                status: active
                ttl: "2026-01-01T00:00:00.000Z"
                owner: "user:alice"
                """,
            Asset.class
        );

        assertThat(asset.getStatus()).isEqualTo("active");
        assertThat(asset.getTtl()).isEqualTo("2026-01-01T00:00:00.000Z");
        assertThat(asset.getOwner()).isEqualTo("user:alice");
        assertThat(asset.getMetadata()).containsEntry(Asset.STATUS_METADATA_KEY, "active");
        assertThat(asset.getMetadata()).doesNotContainKey("status");
    }

    @Test
    void shouldSerialiseTheSystemPropertiesAtRootLevel() throws JsonProcessingException {
        Custom asset = Custom.builder().namespace("io.kestra").id("my-asset").type("EC2").build();
        asset.setStatus("active");
        asset.setOwner("user:alice");

        Map<String, Object> json = JacksonMapper.ofJson().readValue(
            JacksonMapper.ofJson().writeValueAsString(asset),
            new TypeReference<>() {
            }
        );

        assertThat(json).containsEntry("status", "active");
        assertThat(json).containsEntry("owner", "user:alice");
    }

    @Test
    void shouldTellAnEmptyTtlApartFromAnAbsentOne() {
        // An empty value means "no expiry" and must be stored; a null one leaves the lease untouched.
        Custom noExpiry = Custom.builder().namespace("io.kestra").id("a").type("EC2").build();
        noExpiry.setTtl("");
        Custom untouched = Custom.builder().namespace("io.kestra").id("b").type("EC2").build();
        untouched.setTtl(null);

        assertThat(noExpiry.getMetadata()).containsEntry(Asset.TTL_METADATA_KEY, "");
        assertThat(untouched.getMetadata()).doesNotContainKey(Asset.TTL_METADATA_KEY);
    }

    @Test
    void shouldWriteASystemPropertyOnAnAssetBuiltWithoutACreator() {
        Custom asset = new Custom();
        asset.setStatus("active");

        assertThat(asset.getStatus()).isEqualTo("active");
    }

    @Test
    void shouldReadPartOfAndRelatedAsPropertiesNotMetadata() throws Exception {
        String yaml = """
            id: web-1
            type: io.kestra.core.models.assets.External
            partOf:
              id: my-deployment
            related:
              - id: db-1
                role: primary
            metadata:
              size: medium
            """;

        Asset asset = JacksonMapper.ofYaml().readValue(yaml, Asset.class);

        assertThat(asset.getPartOf()).isEqualTo(new AssetRelationRef("my-deployment", null, null));
        assertThat(asset.getRelated()).containsExactly(new AssetRelationRef("db-1", null, "primary"));
        assertThat(asset.getMetadata()).containsOnlyKeys("size");
    }

    @Test
    void shouldKeepPreviousPartOfWhenIncomingIsNull() {
        Asset previous = External.builder().id("web-1").build().withPartOf(new AssetRelationRef("dep-1", null, null));
        Asset incoming = External.builder().id("web-1").build();

        incoming.toUpdated(previous, false);

        assertThat(incoming.getPartOf()).isEqualTo(new AssetRelationRef("dep-1", null, null));
    }

    @Test
    void shouldNotSerializeEmptyRelated() throws Exception {
        Asset asset = External.builder().id("web-1").build().withRelated(List.of());

        String json = JacksonMapper.ofJson().writeValueAsString(asset);

        assertThat(json).doesNotContain("related");
    }
}
