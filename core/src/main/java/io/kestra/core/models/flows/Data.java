package io.kestra.core.models.flows;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Interface for defining an identifiable and typed data.
 */
public interface Data {
    /**
     * The types of data that can be searchable: a scalar value or an array of scalar values.
     */
    Set<Type> SEARCHABLE_TYPES = Collections.unmodifiableSet(EnumSet.of(
        Type.STRING, Type.EMAIL, Type.SELECT, Type.INT, Type.BOOL, Type.FLOAT, Type.URI, Type.DATETIME, Type.DATE, Type.TIME, Type.DURATION, Type.ARRAY
    ));

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

    /**
     * The Display Name for this data.
     *
     * @return a string displayName.
     */
    String getDisplayName();

}
