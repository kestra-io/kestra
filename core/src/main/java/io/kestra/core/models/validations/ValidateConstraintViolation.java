package io.kestra.core.models.validations;

import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.NotNull;

@SuperBuilder(toBuilder = true)
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Introspected
@ToString
@Slf4j
public class ValidateConstraintViolation {
    private String flow;

    private String namespace;

    @NotNull
    private int index;

    private String constraints;

    public String getIdentity(){
        return flow != null & namespace != null ? getFlowId() : flow != null ? flow : "Flow at index " + index;
    }

    public String getFlowId(){
        return namespace+"."+flow;
    }
}
