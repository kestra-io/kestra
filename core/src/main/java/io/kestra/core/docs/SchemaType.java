package io.kestra.core.docs;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.kestra.core.utils.Enums;


public enum SchemaType {
    FLOW,
    TEMPLATE,
    TASK,
    TRIGGER,
    PLUGINDEFAULT,
    APPS,
    TESTSUITES,
    DASHBOARD;

    @JsonCreator
    public static SchemaType fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, SchemaType.class);
    }
}
