package org.kestra.core.models.flows;

import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Value
@Builder
public class Input {
    @NotNull
    @NotBlank
    @Pattern(regexp="[a-zA-Z0-9_-]+")
    private String name;

    @NotNull
    private Type type;

    @NotNull
    private Boolean required = true;

    public enum Type {
        STRING,
        INT,
        FLOAT,
        DATETIME,
        FILE
    }
}
