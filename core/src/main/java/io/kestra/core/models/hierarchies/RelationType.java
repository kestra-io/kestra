package io.kestra.core.models.hierarchies;

public enum RelationType {
    SEQUENTIAL,
    CHOICE,
    ERROR,
    FINALLY,
    PARALLEL,
    DYNAMIC
}
