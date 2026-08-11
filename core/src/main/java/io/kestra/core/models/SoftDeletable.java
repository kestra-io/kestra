package io.kestra.core.models;

/**
 * This interface marks entities that implement a soft deletion mechanism.
 * Soft deletion is based on a <code>deleted</code> field that is set to <code>true</code> when the entity is deleted.
 * Physical deletion either never occurs or occurs in a dedicated purge mechanism.
 */
public interface SoftDeletable<T> {
    /**
     * Whether en entity is deleted or not.
     */
    boolean isDeleted();

    /**
     * Delete the current entity: set its <code>deleted</code> field to <code>true</code>.
     */
    T toDeleted();
}
