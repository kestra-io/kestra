package io.kestra.controller.discovery;

import java.net.URI;

/**
 * Well-known locations for the controller registry in internal storage.
 * <p>
 * Entries are stored as instance resources (namespace = {@code null}, no tenant) under a shared
 * prefix so that every Kestra process can list them.
 */
public final class ControllerRegistry {

    /** Prefix under which all controller registration files are stored. */
    public static final URI REGISTRY_PREFIX = URI.create("/_cluster/controllers/registry/");

    private ControllerRegistry() {
    }

    /**
     * Returns the URI where the registration for the given controller id is stored.
     *
     * @param id the controller JVM identifier.
     * @return the target URI in internal storage.
     */
    public static URI entryUri(final String id) {
        return REGISTRY_PREFIX.resolve(id + ".json");
    }
}
