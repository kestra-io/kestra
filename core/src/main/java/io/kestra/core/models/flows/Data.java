package io.kestra.core.models.flows;

/**
 * Interface for defining on identifiable and typed data.
 */
public interface Data {

    /**
     * The ID for this data.
     *
     * @return a string id.
     */
    String getId();

    /**
     * The Type for this data.
     *
     * @return a type.
     */
    Type getType();
}
