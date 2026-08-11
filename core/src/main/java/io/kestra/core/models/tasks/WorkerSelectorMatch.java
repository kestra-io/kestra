package io.kestra.core.models.tasks;

/**
 * Strategy that controls how {@link WorkerSelector#tags()} are matched against a
 * Worker Queue's tag set.
 */
public enum WorkerSelectorMatch {
    /** Worker Queue tags must be a superset of the selector tags (current behavior). */
    ALL,
    /** Worker Queue tags must intersect the selector tags (non-empty intersection). */
    ANY
}
