package io.kestra.core.services;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.namespaces.NamespaceInterface;
import io.kestra.core.utils.Disposable;

import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Notifies the local listeners that the governance applied to some flows changed, so that what was computed from
 * their governed definition (trigger states, for instance) can be computed again.
 * <p>
 * Notifications stay within the JVM: an edition broadcasting governance changes calls this notifier on every node,
 * once its own view of the governance is up to date.
 */
@Slf4j
@Singleton
public class FlowGovernanceNotifier {
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Registers a listener, called on the notifying thread: it must hand any heavy work off.
     *
     * @return a {@link Disposable} to call to stop listening.
     */
    public Disposable listen(final Listener listener) {
        listeners.add(listener);
        return Disposable.of(() -> listeners.remove(listener));
    }

    /**
     * Notifies that the governance of the given tenant changed, restricted to a namespace subtree when one is given.
     */
    public void notifyChanged(@Nullable final String tenantId, @Nullable final String namespace) {
        notifyListeners(
            flow -> Objects.equals(flow.getTenantId(), tenantId)
                && (namespace == null || NamespaceInterface.isDescendantOrSelf(namespace, flow.getNamespace()))
        );
    }

    public void notifyChangedAll() {
        notifyListeners(flow -> true);
    }

    private void notifyListeners(final Predicate<FlowId> inScope) {
        for (Listener listener : listeners) {
            try {
                listener.onGovernanceChanged(inScope);
            } catch (RuntimeException e) {
                log.error("A flow governance listener failed to process a governance change.", e);
            }
        }
    }

    public interface Listener {
        /**
         * Invoked when the governance of the flows matching {@code inScope} changed.
         */
        void onGovernanceChanged(Predicate<FlowId> inScope);
    }
}
