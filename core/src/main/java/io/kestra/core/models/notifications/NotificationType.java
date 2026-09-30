package io.kestra.core.models.notifications;

/**
 * The kind of event a {@link Notification} carries.
 * <p>
 * Implemented by {@link CoreNotificationType} for the built-in producers, and by any other
 * producer's own enum (e.g. an EE feature's) — a plain Java enum cannot be extended across module
 * boundaries, so this interface is the seam that lets a producer outside this module contribute
 * its own values without this module knowing about them.
 */
public interface NotificationType {
    /**
     * The stable wire/storage value for this type, persisted as {@link Notification#getType()}.
     */
    String key();
}
