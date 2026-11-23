package io.kestra.core.events;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;

/**
 * Who a change is made on behalf of, captured on the request thread so it can travel with an execution
 * command or a kill request to the executor, where there is no request. While the executor applies it, the
 * actor is in the propagated context, and every {@link CrudEvent} created in that scope carries it.
 *
 * @param userId the user who made the change.
 * @param ipAddress the client address of the originating request.
 * @param impersonatedBy the user impersonating {@code userId}, if any.
 */
public record Actor(@Nullable String userId, @Nullable String ipAddress, @Nullable String impersonatedBy) implements PropagatedContextElement {
    /**
     * Puts {@code actor} in the propagated context until the returned scope is closed; a {@code null} actor leaves the
     * context unchanged.
     */
    public static PropagatedContext.Scope propagate(@Nullable Actor actor) {
        PropagatedContext context = PropagatedContext.getOrEmpty();
        return (actor == null ? context : context.plus(actor)).propagate();
    }
}
