package io.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.models.flows.input.*;
import io.micronaut.core.annotation.Introspected;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@SuperBuilder
@Getter
@NoArgsConstructor
@Introspected
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = BooleanInput.class, name = "BOOLEAN"),
    @JsonSubTypes.Type(value = DateInput.class, name = "DATE"),
    @JsonSubTypes.Type(value = DateTimeInput.class, name = "DATETIME"),
    @JsonSubTypes.Type(value = DurationInput.class, name = "DURATION"),
    @JsonSubTypes.Type(value = FileInput.class, name = "FILE"),
    @JsonSubTypes.Type(value = FloatInput.class, name = "FLOAT"),
    @JsonSubTypes.Type(value = IntInput.class, name = "INT"),
    @JsonSubTypes.Type(value = JsonInput.class, name = "JSON"),
    @JsonSubTypes.Type(value = StringInput.class, name = "STRING"),
    @JsonSubTypes.Type(value = TimeInput.class, name = "TIME"),
    @JsonSubTypes.Type(value = URIInput.class, name = "URI")
})
public abstract class Input<T> {
    @NotNull
    @NotBlank
    @Pattern(regexp="^[a-zA-Z0-9][.a-zA-Z0-9_-]*")
    String name;

    @NotNull
    @Valid
    Type type;

    String description;

    @Builder.Default
    Boolean required = true;

    Object defaults;

    public abstract void validate(T input) throws ConstraintViolationException;

    @Introspected
    public enum Type {
        STRING(StringInput.class.getName()),
        INT(IntInput.class.getName()),
        FLOAT(FloatInput.class.getName()),
        BOOLEAN(BooleanInput.class.getName()),
        DATETIME(DateTimeInput.class.getName()),
        DATE(DateInput.class.getName()),
        TIME(TimeInput.class.getName()),
        DURATION(DurationInput.class.getName()),
        FILE(FileInput.class.getName()),
        JSON(JsonInput.class.getName()),
        URI(URIInput.class.getName());

        private final String clsName;

        Type(String clsName) {
            this.clsName = clsName;
        }

        @SuppressWarnings("unchecked")
        public Class<? extends Input<?>> cls() throws ClassNotFoundException {
            return (Class<? extends Input<?>>) Class.forName(this.clsName);
        }
    }
}
