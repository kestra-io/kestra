package io.kestra.core.models.flows;

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
    ASSET(Type.ASSET);

    private final Type parsedAs;

    OutputType(Type parsedAs) {
        this.parsedAs = parsedAs;
    }

    /**
     * @return the type the output value is parsed as.
     */
    public Type parsedAs() {
        return parsedAs;
    }
}
