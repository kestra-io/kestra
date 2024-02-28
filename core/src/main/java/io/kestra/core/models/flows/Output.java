package io.kestra.core.models.flows;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Definition of a flow's output.
 */
@SuperBuilder
@Getter
@NoArgsConstructor
@Introspected
public class Output implements Data {
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
    Object value;

    /**
     * The type of the output.
     */
    @NotNull
    @Valid
    Type type;
}
