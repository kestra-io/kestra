package io.kestra.core.models.flows.input;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.Type;

import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetInputTest {
    @Test
    void singleAssetInputAcceptsOneAssetOnly() {
        AssetInput input = AssetInput.builder().id("vm").type(Type.ASSET).build();

        input.validate(Map.of("id", "web-01"));
        assertThatThrownBy(() -> input.validate(List.of(Map.of("id", "web-01"))))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("it must be a single asset");
    }

    @Test
    void multipleAssetInputAcceptsAListOnly() {
        AssetInput input = AssetInput.builder().id("vms").type(Type.ASSET).multiple(true).build();

        input.validate(List.of(Map.of("id", "web-01")));
        assertThatThrownBy(() -> input.validate(Map.of("id", "web-01")))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("it must be a list of assets");
    }

    @Test
    void renderOnlyRendersExpressionsAndKeepsEveryOtherProperty() {
        AssetInput input = AssetInput.builder()
            .id("vm")
            .type(Type.ASSET)
            .displayName("Virtual machine")
            .assetType("io.kestra.plugin.ee.assets.VM")
            .assetId("{{ prefix }}")
            .namespace("infra")
            .status("{{ status }}")
            .metadata(Map.of("region", "{{ region }}", "replicas", 3))
            .multiple(true)
            .build();

        AssetInput rendered = (AssetInput) input.render(expression -> switch (expression) {
            case "{{ prefix }}" -> "^web";
            case "{{ status }}" -> "retired";
            default -> "eu-west-1";
        });

        assertThat(rendered.getAssetId()).isEqualTo("^web");
        assertThat(rendered.getMetadata()).isEqualTo(Map.of("region", "eu-west-1", "replicas", 3));
        assertThat(rendered.getAssetType()).isEqualTo("io.kestra.plugin.ee.assets.VM");
        assertThat(rendered.getNamespace()).isEqualTo("infra");
        assertThat(rendered.getDisplayName()).isEqualTo("Virtual machine");
        assertThat(rendered.getMultiple()).isTrue();
        assertThat(rendered.getStatus()).isEqualTo("retired");
    }
}
