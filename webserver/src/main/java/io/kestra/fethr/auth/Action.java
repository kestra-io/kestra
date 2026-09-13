package io.kestra.fethr.auth;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What kind of thing a permission lets you do, in increasing order of consequence.
 *
 * <p>
 * Used by the role matrix to badge and order permissions. Serialized lowercase to match the classes
 * the UI badges with.
 */
public enum Action {
    READ,
    WRITE,
    DESTRUCTIVE;

    @JsonValue
    public String value() {
        return name().toLowerCase();
    }
}
