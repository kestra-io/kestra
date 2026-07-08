package io.kestra.core.runners;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.input.FormInput;
import io.kestra.core.models.flows.input.ReusableInputsInput;
import io.kestra.core.serializers.JacksonMapper;

/**
 * Resolves a {@link ReusableInputsInput} reference into the referenced block's inputs, inlined into the flow as if
 * the block were copy-pasted: each input's id is prefixed with the reference id, so children resolve as
 * {@code {{ inputs.<refId>.<childId> }}}. Inlining (rather than wrapping in a single FORM) lets a block contain a
 * {@code FORM} of its own, since the spliced inputs are flattened by the normal input-resolution machinery.
 *
 * <p>
 * A reference may also sit inside a flow's {@code FORM} input; inlining recurses into FORM children, and the block's
 * self-references are scoped to their full runtime path (e.g. {@code inputs.<form>.<refId>...}).
 *
 * <p>
 * This is an Enterprise Edition concern: the open-source default ({@code DisabledReusableInputsExpander}) errors, and
 * the EE implementation looks the block up from its namespace/tenant-scoped store, walking the namespace hierarchy.
 */
public interface ReusableInputsExpander {
    /**
     * Load-bearing signature kept for callers that resolve a top-level reference; delegates to the path-aware overload
     * with no enclosing FORM.
     */
    default List<Input<?>> resolve(String tenantId, String flowNamespace, ReusableInputsInput input) {
        return resolve(tenantId, flowNamespace, input, List.of());
    }

    /**
     * @param tenantId the tenant of the flow being executed
     * @param flowNamespace the namespace of the flow, used as the default when the reference omits one
     * @param input the reference to resolve
     * @param parentPath ids of the enclosing {@code FORM}(s), root&rarr;leaf; empty at the flow root. Used to scope the
     *        inlined block's self-references to their full runtime path when the reference is nested in a
     *        FORM (the spliced ids stay FORM-relative, since {@link Input#expandToLeaves} adds the FORM
     *        prefix).
     * @return the block's inputs, spliced into the flow with each id prefixed by {@code input.getId() + "."}
     */
    List<Input<?>> resolve(String tenantId, String flowNamespace, ReusableInputsInput input, List<String> parentPath);

    /**
     * Inlines every {@link ReusableInputsInput} reference in {@code inputs} — at the top level or nested inside a
     * {@code FORM} — via {@link #resolve}; other inputs pass through unchanged. Returns the list untouched when it holds
     * no reference (directly or in a FORM), so flows without reusable inputs never hit the store. This is the
     * reusable-inputs counterpart of {@link Input#expandToLeaves} — the single place the inlining happens, called from
     * {@code FlowInterface.resolvableInputs(expander)} and the input-resolution paths.
     */
    default List<Input<?>> expand(String tenantId, String flowNamespace, List<Input<?>> inputs) {
        return expandInternal(tenantId, flowNamespace, inputs, List.of());
    }

    private List<Input<?>> expandInternal(String tenantId, String flowNamespace, List<Input<?>> inputs, List<String> parentPath) {
        if (inputs == null || !containsReusable(inputs)) {
            return inputs;
        }

        return inputs.stream()
            .flatMap(input ->
            {
                if (input instanceof ReusableInputsInput reusable) {
                    return resolve(tenantId, flowNamespace, reusable, parentPath).stream();
                }
                if (input instanceof FormInput form && containsReusable(form.getInputs())) {
                    List<String> childPath = new ArrayList<>(parentPath);
                    childPath.add(form.getId());
                    List<Input<?>> expandedChildren = expandInternal(tenantId, flowNamespace, form.getInputs(), childPath);
                    return Stream.<Input<?>> of(withFormInputs(form, expandedChildren));
                }
                return Stream.<Input<?>> of(input);
            })
            .toList();
    }

    private static boolean containsReusable(List<Input<?>> inputs) {
        return inputs != null && inputs.stream().anyMatch(
            input -> input instanceof ReusableInputsInput
                || (input instanceof FormInput form && containsReusable(form.getInputs()))
        );
    }

    /** Re-creates a {@code FORM} with new children via a Jackson round-trip ({@code FormInput} has no {@code toBuilder}). */
    private static FormInput withFormInputs(FormInput form, List<Input<?>> newInputs) {
        Map<String, Object> map = JacksonMapper.toMap(form);
        map.put("inputs", newInputs);
        return (FormInput) JacksonMapper.toMap(map, Input.class);
    }
}
