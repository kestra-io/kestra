package org.floworc.core.flows;


import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class Inputs {
    @NotNull
    private String name;

    private Class type;
}
