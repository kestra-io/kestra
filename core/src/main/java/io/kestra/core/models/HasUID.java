package io.kestra.core.models;

/**
 * A UID is used to uniquely identify an entity across an entire Kestra's cluster.
 * <p>
 * A UID is either a unique random ID or composed of a combination of entity properties such as
 * the associated tenant, namespace, and identifier.
 * <p>
 * For Kestra's queuing mechanism the UID can be used as routing/or partitioning key.
 */
public interface HasUID {

    /**
     * Gets the UID attached to this entity.
     * <p>
     * Be careful when modifying the implementation of this method for subclasses, as it should be consistent over time.
     *
     * @return the string uid.
     */
    String uid();
}
