package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.input.AssetInput;

import java.util.List;

import jakarta.annotation.Nullable;

/**
 * Checks the value of an {@code ASSET} input or output (an asset id, or a list of ids) against the asset store and the input's filters.
 * <p>
 * This is an Enterprise Edition concern: the open-source default ({@code DisabledAssetInputResolver}) errors.
 */
public interface AssetInputResolver {
    /**
     * @param input the declaring input, whose filters the assets must match, or {@code null} for a flow output
     * @return the asset id, or the list of ids when the input accepts {@code multiple} assets
     */
    Object resolve(Execution execution, @Nullable AssetInput input, Object value);

    /**
     * @return the ids among {@code ids} that the current user cannot find, that do not match the filters of {@code input},
     *         or that a flow of {@code flowNamespace} cannot reach
     */
    default List<String> unknownIds(String tenantId, String flowNamespace, AssetInput input, List<String> ids) {
        return List.of();
    }

    /**
     * Refuses an edit that {@link #store} could not write, before any input is stored, so that an edit refused here
     * leaves no other edit written. The writes are not transactional: one that fails later in {@link #store}, or an
     * execution that is never emitted, can leave the edits stored before it in place.
     */
    default void checkWrite(Execution execution, AssetInput input, Object resolved) {
    }

    /**
     * Reduces a resolved value to what the execution being created stores. Only called on a real execution creation,
     * never to validate the inputs, and once every input and flow check has passed.
     *
     * @param write whether to persist the edits made in the form when {@code persistOverrides} asks for it; when
     *              {@code false}, the edits are kept on the execution instead, so a replay submits them again
     */
    default Object store(Execution execution, AssetInput input, Object resolved, boolean write) {
        return AssetInput.storedValue(resolved);
    }
}
