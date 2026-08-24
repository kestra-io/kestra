package io.kestra.core.models.assets;

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
    void shouldKeepPreviousTypeAndNamespaceWhenNotDeclared() {
        Asset previous = Custom.builder()
            .namespace("io.kestra")
            .id("my-asset")
            .type("MY_OWN_ASSET_TYPE")
            .displayName("My asset")
            .description("This is my asset")
            .build();

        Asset updated = External.builder()
            .id("my-asset")
            .metadata(Map.of("owner", "infra-team"))
            .build()
            .toUpdated(previous);

        assertThat(updated.getType()).isEqualTo("MY_OWN_ASSET_TYPE");
        assertThat(updated.getNamespace()).isEqualTo("io.kestra");
        assertThat(updated.getDisplayName()).isEqualTo("My asset");
        assertThat(updated.getDescription()).isEqualTo("This is my asset");
        assertThat(updated.getMetadata()).containsEntry("owner", "infra-team");
    }

    @Test
    void shouldKeepPreviousNamespaceWhenAnotherOneIsDeclared() {
        Asset previous = Custom.builder()
            .namespace("io.kestra")
            .id("my-asset")
            .type("MY_OWN_ASSET_TYPE")
            .build();

        Asset updated = Custom.builder()
            .namespace("io.kestra.other")
            .id("my-asset")
            .type("MY_OWN_ASSET_TYPE")
            .build()
            .toUpdated(previous);

        assertThat(updated.getNamespace()).isEqualTo("io.kestra");
    }
}
