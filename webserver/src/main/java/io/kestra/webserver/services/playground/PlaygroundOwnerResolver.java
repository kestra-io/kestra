package io.kestra.webserver.services.playground;

import java.util.Optional;

import jakarta.inject.Singleton;

/**
 * The caller's name as written in the {@code system.username} label of the runs they start; empty when runs carry no owner, so a session spans the whole flow.
 */
@Singleton
public class PlaygroundOwnerResolver {
    public Optional<String> owner() {
        return Optional.empty();
    }
}
