package io.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

/**
 * The supported data types of a flow output.
 */
public enum OutputType {
    STRING(Type.STRING),
    INT(Type.INT),
    FLOAT(Type.FLOAT),
    BOOL(Type.BOOL),
    DATETIME(Type.DATETIME),
    DATE(Type.DATE),
    TIME(Type.TIME),
    DURATION(Type.DURATION),
    FILE(Type.FILE),
    JSON(Type.JSON),
    ION(Type.ION),
    URI(Type.URI),
    SECRET(Type.SECRET),
    ARRAY(Type.ARRAY),
    YAML(Type.YAML),
    ASSET(Type.ASSET),
    /** A type this version does not know, such as an input-only type, kept so the flow still loads and runs. */
    UNKNOWN(null);

    private final Type parsedAs;

    OutputType(Type parsedAs) {
        this.parsedAs = parsedAs;
    }

    @JsonCreator
    public static OutputType fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, OutputType.class, UNKNOWN);
    }

    /** The input type an output value is parsed as, or {@code null} for {@link #UNKNOWN}, whose value is kept as it is. */
    public Type parsedAs() {
        return parsedAs;
    }
}
