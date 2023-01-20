package io.kestra.core.models.tasks.common;

/**
 * Enumeration that can be used to define how a task that fetch data will fetch it.
 * It is designed to be used in conjunction with a task output of type {@link FetchOutput}.
 */
public enum FetchType {
    /** Fetched data will be stored in Kestra storage. */
    STORE,

    /** Fetched data will be available as a list of objects. */
    FETCH,

    /** Fetched data will be available as a single object. */
    FETCH_ONE,

    /** Data will not be fetched. */
    NONE
}
