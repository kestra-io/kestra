package org.floworc.core.models.flows;

import lombok.Value;

import javax.validation.constraints.NotNull;

@Value
public class Input {
    @NotNull
    private String name;

    private Type type;

    private Boolean required = true;

    public enum Type {
        STRING,
        INT,
        FLOAT,
        DATETIME,
        FILE
    }
}
