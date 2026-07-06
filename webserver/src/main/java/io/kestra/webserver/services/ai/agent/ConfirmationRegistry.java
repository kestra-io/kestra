package io.kestra.webserver.services.ai.agent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Singleton;

@Singleton
public class ConfirmationRegistry {
    private final Map<String, SuspendedTurn> suspended = new ConcurrentHashMap<>();

    public void park(final SuspendedTurn turn) {
        suspended.put(turn.confirmationId(), turn);
    }

    public Optional<SuspendedTurn> take(final String confirmationId) {
        return Optional.ofNullable(suspended.remove(confirmationId));
    }
}
