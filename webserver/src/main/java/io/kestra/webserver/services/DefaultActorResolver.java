package io.kestra.webserver.services;

import io.kestra.core.events.Actor;

import jakarta.inject.Singleton;

/**
 * Default resolver: with a single account there is no user to attribute a change to.
 */
@Singleton
public class DefaultActorResolver implements ActorResolver {
    @Override
    public Actor resolve() {
        return null;
    }
}
