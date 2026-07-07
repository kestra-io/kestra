package io.kestra.core.runners;

import java.util.List;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.input.ReusableInputsInput;

import jakarta.inject.Singleton;

/**
 * Open-source default: reusable inputs are an Enterprise Edition feature, so any attempt to resolve a
 * {@code REUSABLE_INPUTS} reference fails. The EE build replaces this with a store-backed implementation.
 */
@Singleton
public class DisabledReusableInputsExpander implements ReusableInputsExpander {
    @Override
    public List<Input<?>> resolve(String tenantId, String flowNamespace, ReusableInputsInput input, List<String> parentPath) {
        throw new IllegalArgumentException(
            "Reusable inputs require Kestra Enterprise Edition (input '" + input.getId() +
                "' references reusable inputs '" + input.getRef() + "')."
        );
    }
}
