package io.kestra.core.models.flows;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kestra.core.models.flows.input.AssetInput;
import io.kestra.core.validations.OutputValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Definition of a flow's output.
 */
@SuperBuilder
@Getter
@NoArgsConstructor
@OutputValidation
public class Output implements Data {
    /**
     * The types of output that can be searchable: a scalar value or an array of scalar values.
     */
    public static final Set<OutputType> SEARCHABLE_TYPES = Collections.unmodifiableSet(EnumSet.of(
        OutputType.STRING, OutputType.INT, OutputType.BOOL, OutputType.FLOAT, OutputType.URI, OutputType.DATETIME, OutputType.DATE, OutputType.TIME, OutputType.DURATION, OutputType.ARRAY
    ));

    /**
     * The output's unique id.
     */
    @NotNull
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9][.a-zA-Z0-9_-]*")
    String id;

    /**
     * Short description of the output.
     */
    String description;

    /**
     * The output value. Can be a dynamic expression.
     */
    @NotNull
    @Schema(
        oneOf = {
            Object.class,
            String.class
        }
    )
    Object value;

    /**
     * The type of the output.
     */
    @NotNull
    @Valid
    OutputType type;

    String displayName;

    /**
     * Specifies whether the output is required or not.
     * <p>
     * By default, an output is always required.
     */
    Boolean required;

    /**
     * Specifies whether the output is searchable or not.
     * A searchable output will be stored in a separate table with its own index.
     * It's only meaningful for execution outputs.
     * <p>
     * By default, an output is not searchable.
     */
    Boolean searchable;

    /**
     * @return the output values as an execution stores them, an {@code ASSET} output being reduced to the asset id
     */
    public static Map<String, Object> storedValues(List<Output> outputs, Map<String, Object> values) {
        if (outputs == null || values == null) {
            return values;
        }
        Map<String, Object> stored = new HashMap<>(values);
        outputs.stream()
            .filter(output -> OutputType.ASSET == output.getType() && stored.get(output.getId()) != null)
            .forEach(output -> stored.put(output.getId(), AssetInput.storedValue(stored.get(output.getId()))));
        return stored;
    }
}
