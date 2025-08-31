package io.kestra.core.models.hierarchies;

public enum RelationType {
    SEQUENTIAL,
    CHOICE,
    ERROR,
    FINALLY,
    AFTER_EXECUTION,
    PARALLEL,
    DYNAMIC
}
