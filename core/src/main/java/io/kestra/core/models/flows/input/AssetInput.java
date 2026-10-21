package io.kestra.core.models.flows.input;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.RenderableInput;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.utils.PebbleUtil;
import io.kestra.core.validations.AssetInputValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
@EeOnly
@AssetInputValidation
public class AssetInput extends Input<Object> implements RenderableInput {
    @Schema(
        title = "The type of the assets that can be selected.",
        description = "A registered asset type such as `io.kestra.plugin.ee.assets.VM`, or any custom type name."
    )
    private String assetType;

    @Schema(
        title = "A regular expression the asset id must match.",
        description = "Matches anywhere in the id, e.g. `^web` keeps `web-01` and `web-02`."
    )
    private String assetId;

    @Schema(
        title = "The namespace of the assets that can be selected.",
        description = "Child namespaces are included: `company.team` also matches `company.team.backend`."
    )
    private String namespace;

    @Schema(
        title = "The status the asset must have.",
        description = "An asset with no status counts as `active`, as on the Assets page."
    )
    private String status;

    @Schema(
        title = "The metadata the asset must have.",
        description = "Each key must be equal to the given value, as in the `assets()` function."
    )
    private Map<String, Object> metadata;

    @Schema(
        title = "Whether several assets can be selected.",
        description = "The value is then a list of assets."
    )
    @NotNull
    @Builder.Default
    private Boolean multiple = false;

    /**
     * Reduces a resolved value to what an execution stores: the asset id, or the list of ids when {@code multiple}.
     */
    public static Object storedValue(Object resolved) {
        if (resolved instanceof List<?> list) {
            return list.stream().map(AssetInput::storedValue).toList();
        }
        if (resolved instanceof Map<?, ?> asset && asset.get("id") != null) {
            return asset.get("id").toString();
        }
        return resolved;
    }

    @Override
    public void validate(Object input) throws ConstraintViolationException {
        boolean isList = input instanceof List<?>;
        if (isList != Boolean.TRUE.equals(multiple)) {
            throw ManualConstraintViolation.toConstraintViolationException(
                Boolean.TRUE.equals(multiple) ? "it must be a list of assets" : "it must be a single asset",
                this,
                AssetInput.class,
                getId(),
                input
            );
        }
    }

    @Override
    public Input<?> render(Function<String, Object> renderer) {
        return AssetInput.builder()
            .id(getId())
            .type(getType())
            .description(getDescription())
            .dependsOn(getDependsOn())
            .required(getRequired())
            .defaults(getDefaults())
            .prefill(getPrefill())
            .displayName(getDisplayName())
            .multiple(multiple)
            .assetType(renderString(assetType, renderer))
            .assetId(renderString(assetId, renderer))
            .namespace(renderString(namespace, renderer))
            .status(renderString(status, renderer))
            .metadata(renderMetadata(renderer))
            .build();
    }

    private Map<String, Object> renderMetadata(Function<String, Object> renderer) {
        if (metadata == null) {
            return null;
        }
        Map<String, Object> rendered = new LinkedHashMap<>();
        metadata.forEach((key, value) -> rendered.put(key, value instanceof String s ? renderString(s, renderer) : value));
        return rendered;
    }

    private static String renderString(String value, Function<String, Object> renderer) {
        if (value == null || !PebbleUtil.containsOpeningBlockDelimiter(value)) {
            return value;
        }
        Object rendered = renderer.apply(value);
        return rendered == null ? null : rendered.toString();
    }
}
