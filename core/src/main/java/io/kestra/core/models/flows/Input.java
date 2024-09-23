package io.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.models.flows.input.*;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuppressWarnings("deprecation")
@SuperBuilder
@Getter
@NoArgsConstructor
@Introspected
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ArrayInput.class, name = "ARRAY"),
    @JsonSubTypes.Type(value = BooleanInput.class, name = "BOOLEAN"),
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
    @JsonSubTypes.Type(value = YamlInput.class, name = "YAML")
})
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public abstract class Input<T> implements Data {
    @NotNull
    @NotBlank
    @Pattern(regexp="^[a-zA-Z0-9][.a-zA-Z0-9_-]*")
    String id;

    @Deprecated
    String name;

    @NotNull
    @Valid
    Type type;

    String description;

    DependsOn dependsOn;

    @Builder.Default
    Boolean required = true;

    Object defaults;

    @Schema(
        title = "The display name of the input."
    )
    @Size(max = 64)
    String displayName;

    public abstract void validate(T input) throws ConstraintViolationException;

    @JsonSetter
    public void setName(String name) {
        if (this.id == null) {
            this.id = name;
        }

        this.name = name;
    }
}
