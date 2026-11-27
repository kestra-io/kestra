package io.kestra.core.vnodes;

import java.util.function.Consumer;

import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.Disposable;

/**
 * The group of services sharing a set of vNodes, and the transport of their {@link VNodesAssignmentEvent}s.
 */
public interface VNodesAssignmentChannel {

    /**
     * @return the type of the services the vNodes are assigned to.
     */
    ServiceType serviceType();

    /**
     * @return the id of the lock electing the controller of this group, among the {@code vnodes} locks.
     */
    String controllerLockId();

    void send(VNodesAssignmentEvent event);

    Disposable subscribe(Consumer<VNodesAssignmentEvent> consumer);
}
