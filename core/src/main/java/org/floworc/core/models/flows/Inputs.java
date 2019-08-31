package org.floworc.core.models.flows;

import lombok.Value;

import javax.validation.constraints.NotNull;

@Value
public class Inputs {
    @NotNull
    private String name;

    private Class type;
}
