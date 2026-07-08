package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * References a reusable inputs block defined at the namespace (or tenant) level. Before an execution
 * starts the reference is replaced by a {@link FormInput} carrying the block's inputs as children, so
 * the children resolve under the reference id as a nested path (e.g. {@code {{ inputs.myRef.anInput }}}),
 * exactly like a hand-written {@code FORM}.
 *
 * <p>
 * This is an Enterprise Edition feature: the block storage and resolution live in EE. The type is
 * registered here only because the {@code type} discriminator is a closed {@link io.kestra.core.models.flows.Type}
 * enum; the open-source build cannot resolve it (the default expander errors) and it is hidden from the
 * open-source flow schema.
 */
@SuperBuilder
@Getter
@NoArgsConstructor
@EeOnly
public class ReusableInputsInput extends Input<Void> {
    @Schema(
        title = "The id of the reusable inputs block to reference."
    )
    @NotNull
    @NotBlank
    private String ref;

    @Schema(
        title = "The namespace where the reusable inputs block is defined.",
        description = "Optional, defaults to the flow's namespace. Resolution walks the namespace hierarchy, " +
            "so a block defined in a parent namespace is usable from its child namespaces."
    )
    private String namespace;

    @Schema(
        title = "Pin a specific revision of the reusable inputs block.",
        description = "Optional, defaults to the latest revision."
    )
    private Integer revision;

    @Override
    public void validate(Void input) throws ConstraintViolationException {
        // no-op: a REUSABLE_INPUTS reference is a structural wrapper, replaced by its referenced block before resolution
    }
}
