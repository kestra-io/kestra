package io.kestra.core.runners;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.DependsOn;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Output;
import io.kestra.core.models.flows.OutputType;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.AssetInput;
import io.kestra.core.models.flows.input.FormInput;
import io.kestra.core.models.flows.input.InputAndValue;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.IdUtils;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class FlowInputOutputAssetTest {
    private static final Execution EXECUTION = Execution.builder()
        .id(IdUtils.create())
        .flowId(IdUtils.create())
        .flowRevision(1)
        .namespace("io.kestra.test")
        .build();

    private static final List<AssetInput> RESOLVED_FOR = new ArrayList<>();

    @Inject
    FlowInputOutput flowInputOutput;

    @MockBean(AssetInputResolver.class)
    AssetInputResolver assetInputResolver() {
        return (execution, input, value) ->
        {
            RESOLVED_FOR.add(input);
            if (value instanceof List<?> ids) {
                return ids.stream().map(id -> Map.of("id", id)).toList();
            }
            return Map.of("id", value);
        };
    }

    @BeforeEach
    void reset() {
        RESOLVED_FOR.clear();
    }

    @Test
    void resolvesTheSubmittedAssetIdThroughTheResolver() {
        AssetInput input = AssetInput.builder().id("vm").type(Type.ASSET).assetType("io.kestra.plugin.ee.assets.VM").build();

        List<InputAndValue> values = flowInputOutput.resolveInputs(List.of(input), null, EXECUTION, Map.of("vm", "web-01"));

        assertThat(values.getFirst().exceptions()).isNullOrEmpty();
        assertThat(values.getFirst().value()).isEqualTo(Map.of("id", "web-01"));
        assertThat(RESOLVED_FOR).extracting(AssetInput::getAssetType).containsExactly("io.kestra.plugin.ee.assets.VM");
    }

    @Test
    void resolvesTheDefaultAssetId() {
        AssetInput input = AssetInput.builder().id("vm").type(Type.ASSET).defaults(Property.ofValue("web-01")).build();

        List<InputAndValue> values = flowInputOutput.resolveInputs(List.of(input), null, EXECUTION, Map.of());

        assertThat(values.getFirst().value()).isEqualTo(Map.of("id", "web-01"));
    }

    @Test
    void resolvesAListOfAssetsWhenMultiple() {
        AssetInput input = AssetInput.builder().id("vms").type(Type.ASSET).multiple(true).build();

        List<InputAndValue> values = flowInputOutput.resolveInputs(List.of(input), null, EXECUTION, Map.of("vms", List.of("web-01", "web-02")));

        assertThat(values.getFirst().exceptions()).isNullOrEmpty();
        assertThat(values.getFirst().value()).isEqualTo(List.of(Map.of("id", "web-01"), Map.of("id", "web-02")));
    }

    @Test
    void resolvesMultipleDefaultAssetIds() {
        AssetInput input = AssetInput.builder().id("vms").type(Type.ASSET).multiple(true).defaults(Property.ofValue(List.of("web-01", "web-02"))).build();

        List<InputAndValue> values = flowInputOutput.resolveInputs(List.of(input), null, EXECUTION, Map.of());

        assertThat(values.getFirst().value()).isEqualTo(List.of(Map.of("id", "web-01"), Map.of("id", "web-02")));
    }

    @Test
    void rejectsAListForASingleAssetInput() {
        AssetInput input = AssetInput.builder().id("vm").type(Type.ASSET).build();

        List<InputAndValue> values = flowInputOutput.resolveInputs(List.of(input), null, EXECUTION, Map.of("vm", List.of("web-01", "web-02")));

        assertThat(values.getFirst().exceptions()).isNotEmpty();
        assertThat(values.getFirst().exceptions().iterator().next().getMessage()).contains("it must be a single asset");
    }

    @Test
    void rendersTheFiltersWithTheInputsItDependsOn() {
        StringInput deployment = StringInput.builder().id("deployment").type(Type.STRING).build();
        AssetInput vm = AssetInput.builder()
            .id("vm")
            .type(Type.ASSET)
            .namespace("{{ inputs.deployment }}.compute")
            .metadata(Map.of("deployment", "{{ inputs.deployment }}", "replicas", 3))
            .dependsOn(new DependsOn(List.of("deployment"), null))
            .build();

        List<InputAndValue> values = flowInputOutput.resolveInputs(List.of(deployment, vm), null, EXECUTION, Map.of("deployment", "prod", "vm", "web-01"));

        assertThat(values.get(1).exceptions()).isNullOrEmpty();
        AssetInput rendered = RESOLVED_FOR.getFirst();
        assertThat(rendered.getNamespace()).isEqualTo("prod.compute");
        assertThat(rendered.getMetadata()).isEqualTo(Map.of("deployment", "prod", "replicas", 3));
        assertThat(values.get(1).input()).isInstanceOfSatisfying(AssetInput.class, input -> assertThat(input.getNamespace()).isEqualTo("prod.compute"));
    }

    @Test
    void storesOnlyTheIdOfAnAssetNestedInAForm() {
        FormInput form = FormInput.builder()
            .id("target")
            .type(Type.FORM)
            .inputs(List.<Input<?>> of(AssetInput.builder().id("vm").type(Type.ASSET).build()))
            .build();

        Map<String, Object> resolved = flowInputOutput.readExecutionInputs(
            Flow.builder().id("flow").namespace("io.kestra.test").inputs(List.of(form)).build(),
            EXECUTION,
            Map.of("target.vm", "web-01")
        );

        assertThat(resolved).isEqualTo(Map.of("target", Map.of("vm", "web-01")));
    }

    @Test
    void resolvesAnAssetOutputWithoutFilters() {
        Flow flow = Flow.builder()
            .id("flow")
            .namespace("io.kestra.test")
            .outputs(List.of(Output.builder().id("vm").type(OutputType.ASSET).value("{{ inputs.vm }}").build()))
            .build();

        Map<String, Object> outputs = flowInputOutput.typedOutputs(flow, EXECUTION, Map.of("vm", "web-01"));

        assertThat(outputs).isEqualTo(Map.of("vm", Map.of("id", "web-01")));
        assertThat(RESOLVED_FOR).containsExactly((AssetInput) null);
    }
}
