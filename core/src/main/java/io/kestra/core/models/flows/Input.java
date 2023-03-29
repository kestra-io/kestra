package io.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.validations.InputValidation;
import io.kestra.core.validations.Regex;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@SuperBuilder
@Getter
@NoArgsConstructor
@Introspected
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@InputValidation
public class Input {
    @NotNull
    @NotBlank
    @Pattern(regexp="[.a-zA-Z0-9_-]+")
    String name;

    @NotBlank
    @NotNull
    @Valid
    Type type;

    String description;

    @Builder.Default
    Boolean required = true;

    String defaults;

    @Schema(
        title = "Regular expression validating the value."
    )
    @Regex
    String validator;

    @JsonIgnore
    public boolean canBeValidated() {
        if (type == null) {
            return false;
        }
        return type.canBeValidated();
    }

    @Introspected
    public enum Type {
        STRING() {
            @Override
            public boolean canBeValidated() {
                return true;
            }
        },
        INT,
        FLOAT,
        BOOLEAN,
        DATETIME,
        DATE,
        TIME,
        DURATION,
        FILE,
        JSON,
        URI;

        public boolean canBeValidated() {
            return false;
        }
    }
}
