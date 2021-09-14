package io.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
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
public class Input {
    @NotNull
    @NotBlank
    @Pattern(regexp="[a-zA-Z0-9_-]+")
    String name;

    @NotBlank
    @NotNull
    @Valid
    Type type;

    String description;

    @Builder.Default
    Boolean required = true;

    String defaults;

    @Introspected
    public enum Type {
        STRING,
        INT,
        FLOAT,
        DATETIME,
        FILE
    }
}
