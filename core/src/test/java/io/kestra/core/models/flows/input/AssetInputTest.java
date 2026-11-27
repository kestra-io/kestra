package io.kestra.core.models.flows.input;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.Type;
import io.kestra.core.models.property.Property;

import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetInputTest {
    @Test
    void shouldRejectAListWhenTheAssetInputIsSingle() {
        AssetInput input = AssetInput.builder().id("vm").type(Type.ASSET).build();

        input.validate(Map.of("id", "web-01"));
        assertThatThrownBy(() -> input.validate(List.of(Map.of("id", "web-01"))))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("it must be a single asset");
    }

    @Test
    void shouldRejectASingleAssetWhenTheAssetInputIsMultiple() {
        AssetInput input = AssetInput.builder().id("vms").type(Type.ASSET).multiple(true).build();

        input.validate(List.of(Map.of("id", "web-01")));
        assertThatThrownBy(() -> input.validate(Map.of("id", "web-01")))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("it must be a list of assets");
    }

    @Test
    void shouldRejectAnEmptyListWhenTheMultipleAssetInputIsRequired() {
        AssetInput required = AssetInput.builder().id("vms").type(Type.ASSET).multiple(true).build();
        AssetInput optional = AssetInput.builder().id("vms").type(Type.ASSET).multiple(true).required(false).build();

        optional.validate(List.of());
        assertThatThrownBy(() -> required.validate(List.of()))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("it must contain at least one asset");
    }

    @Test
    void shouldKeepEveryOtherPropertyWhenRenderingTheFilters() {
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

    @Test
    void shouldReadTheDefaultIdsWhenWrittenLiterally() {
        assertThat(withDefaults(Property.ofValue("web-01")).staticDefaultIds()).containsExactly("web-01");
        assertThat(withDefaults(Property.ofValue(List.of("web-01", "db-01"))).staticDefaultIds()).containsExactly("web-01", "db-01");
        assertThat(withDefaults(Property.ofExpression("{{ inputs.vm }}")).staticDefaultIds()).isEmpty();
        assertThat(withDefaults(Property.ofValue(Map.of("id", "web-01", "overrides", Map.of("owner", "bob")))).staticDefaultIds()).containsExactly("web-01");
        assertThat(withDefaults(Property.ofValue(List.of(Map.of("id", "web-01"), "db-01"))).staticDefaultIds()).containsExactly("web-01", "db-01");
        assertThat(withDefaults(Property.ofValue("[web-01")).staticDefaultIds()).isEmpty();
        assertThat(withDefaults(null).staticDefaultIds()).isEmpty();
    }

    @Test
    void shouldHaveDynamicFiltersOnlyWhenAFilterIsAnExpression() {
        assertThat(AssetInput.builder().id("vm").type(Type.ASSET).namespace("infra").metadata(Map.of("env", "prod")).build().hasDynamicFilters()).isFalse();
        assertThat(AssetInput.builder().id("vm").type(Type.ASSET).namespace("{{ flow.namespace }}").build().hasDynamicFilters()).isTrue();
        assertThat(AssetInput.builder().id("vm").type(Type.ASSET).metadata(Map.of("env", "{{ inputs.env }}")).build().hasDynamicFilters()).isTrue();
    }

    private static AssetInput withDefaults(Property<Object> defaults) {
        return AssetInput.builder().id("vm").type(Type.ASSET).defaults(defaults).build();
    }
}
