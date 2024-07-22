package io.kestra.core.models.flows;

import io.kestra.core.models.flows.input.*;
import io.micronaut.core.annotation.Introspected;

/**
 * The supported data types.
 */
@Introspected
public enum Type {
    STRING(StringInput.class.getName()),
    ENUM(EnumInput.class.getName()),
    SELECT(SelectInput.class.getName()),
    INT(IntInput.class.getName()),
    FLOAT(FloatInput.class.getName()),
    BOOLEAN(BooleanInput.class.getName()),
    DATETIME(DateTimeInput.class.getName()),
    DATE(DateInput.class.getName()),
    TIME(TimeInput.class.getName()),
    DURATION(DurationInput.class.getName()),
    FILE(FileInput.class.getName()),
    JSON(JsonInput.class.getName()),
    URI(URIInput.class.getName()),
    SECRET(SecretInput.class.getName()),
    ARRAY(ArrayInput.class.getName()),
    MULTISELECT(MultiselectInput.class.getName());

    private final String clsName;

    Type(String clsName) {
        this.clsName = clsName;
    }

    @SuppressWarnings("unchecked")
    public Class<? extends Input<?>> cls() {
        try {
            return (Class<? extends Input<?>>) Class.forName(this.clsName);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
