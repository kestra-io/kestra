package io.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.kestra.core.models.flows.input.*;
import io.kestra.core.models.property.Property;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.validations.InputValidation;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
@SuperBuilder
@Getter
@NoArgsConstructor
@Introspected
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = ArrayInput.class, name = "ARRAY"),
        @JsonSubTypes.Type(value = BooleanInput.class, name = "BOOLEAN"),
        @JsonSubTypes.Type(value = BoolInput.class, name = "BOOL"),
        @JsonSubTypes.Type(value = DateInput.class, name = "DATE"),
        @JsonSubTypes.Type(value = DateTimeInput.class, name = "DATETIME"),
        @JsonSubTypes.Type(value = DurationInput.class, name = "DURATION"),
        @JsonSubTypes.Type(value = FileInput.class, name = "FILE"),
        @JsonSubTypes.Type(value = FloatInput.class, name = "FLOAT"),
        @JsonSubTypes.Type(value = IntInput.class, name = "INT"),
        @JsonSubTypes.Type(value = JsonInput.class, name = "JSON"),
        @JsonSubTypes.Type(value = SecretInput.class, name = "SECRET"),
        @JsonSubTypes.Type(value = StringInput.class, name = "STRING"),
        @JsonSubTypes.Type(value = EnumInput.class, name = "ENUM"),
        @JsonSubTypes.Type(value = SelectInput.class, name = "SELECT"),
        @JsonSubTypes.Type(value = TimeInput.class, name = "TIME"),
        @JsonSubTypes.Type(value = URIInput.class, name = "URI"),
        @JsonSubTypes.Type(value = MultiselectInput.class, name = "MULTISELECT"),
        @JsonSubTypes.Type(value = YamlInput.class, name = "YAML"),
        @JsonSubTypes.Type(value = EmailInput.class, name = "EMAIL"),
        @JsonSubTypes.Type(value = FormInput.class, name = "FORM"),
    }
)
@InputValidation
public abstract class Input<T> implements Data {
    @Schema(
        title = "The ID of the input."
    )
    @NotNull
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9][.a-zA-Z0-9_-]*")
    String id;

    @Deprecated
    String name;

    @Schema(
        title = "The type of the input."
    )
    @NotNull
    @Valid
    Type type;

    @Schema(
        title = "The description of the input."
    )
    String description;

    @Schema(
        title = "The dependencies of the input."
    )
    DependsOn dependsOn;

    @Builder.Default
    Boolean required = true;

    @Schema(
        title = "The default value to use if no value is specified."
    )
    Property<T> defaults;

    @Schema(
        title = "The suggested value for the input.",
        description = "Optional UI hint for pre-filling the input. Cannot be used together with a default value."
    )
    Property<T> prefill;

    @Schema(
        title = "The display name of the input."
    )
    String displayName;

    public abstract void validate(T input) throws ConstraintViolationException;

    @JsonSetter
    public void setName(String name) {
        if (this.id == null) {
            this.id = name;
        }

        this.name = name;
    }

    /**
     * Expands every {@link io.kestra.core.models.flows.input.FormInput} into copies of its children whose id is
     * rewritten to the dotted path ({@code environment} + {@code .} + {@code region} -> {@code environment.region}).
     * <p>
     * The resolution core keys inputs by {@link #getId()} and reassembles dotted keys into a nested map via
     * {@link io.kestra.core.utils.MapUtils#flattenToNestedMap(java.util.Map)}, so a leaf carrying the dotted id is all
     * that is needed for the nested payload to materialize. {@code dependsOn} is intentionally NOT rewritten — authors
     * reference siblings by their full dotted path; an unresolved bare ref is rejected loudly by flow validation.
     * Forms cannot be nested (enforced by validation), so expansion is single-level.
     *
     * @return a flat list of leaf inputs with no {@code FORM} node surviving, or the input list unchanged when null/empty.
     */
    public static List<Input<?>> expandToLeaves(List<Input<?>> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return inputs;
        }

        List<Input<?>> leaves = new ArrayList<>();
        for (Input<?> input : inputs) {
            if (input instanceof io.kestra.core.models.flows.input.FormInput form) {
                if (form.getInputs() != null) {
                    for (Input<?> child : form.getInputs()) {
                        leaves.add(copyWithId(child, form.getId() + "." + child.getId()));
                    }
                }
            } else {
                leaves.add(input);
            }
        }
        return leaves;
    }

    /**
     * Copies {@code input} with its {@code id} replaced by {@code newId}, preserving the concrete subtype.
     * <p>
     * Done via a Jackson round-trip rather than a builder: {@code @SuperBuilder(toBuilder=...)} does not generate
     * {@code toBuilder()} on this abstract class, and the resolution core casts leaves to their concrete subtype
     * ({@code (ArrayInput) input}, {@code case StringInput i -> ...}). The round-trip re-resolves the subtype through
     * {@link JsonTypeInfo} on the {@code type} property, so {@code copyWithId(stringInput, ...) instanceof StringInput}.
     */
    private static Input<?> copyWithId(Input<?> input, String newId) {
        Map<String, Object> map = JacksonMapper.toMap(input);
        map.put("id", newId);
        return JacksonMapper.toMap(map, Input.class);
    }

    /**
     * @return all expanded leaf paths (e.g. {@code environment.region}, {@code credentials.api_key}, {@code api_key}),
     * used by uniqueness validation to reject duplicate paths and prefix conflicts.
     */
    public static List<String> collectExpandedPaths(List<Input<?>> inputs) {
        List<Input<?>> expanded = expandToLeaves(inputs);
        return expanded == null ? List.of() : expanded.stream().map(Input::getId).toList();
    }
}
