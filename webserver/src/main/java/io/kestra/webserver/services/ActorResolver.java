package io.kestra.webserver.services;

import io.kestra.core.events.Actor;

import io.micronaut.core.annotation.Nullable;

/**
 * Resolves who the current request acts on behalf of. It reads the request-scoped security context, so it
 * must be called on the request thread.
 */
public interface ActorResolver {
    @Nullable
    Actor resolve();
}
