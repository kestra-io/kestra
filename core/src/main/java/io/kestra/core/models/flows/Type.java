package io.kestra.core.models.flows;

import io.kestra.core.models.flows.input.*;

/**
 * The supported data types.
 */
public enum Type {
    STRING(StringInput.class),
    SELECT(SelectInput.class),
    INT(IntInput.class),
    FLOAT(FloatInput.class),
    BOOL(BoolInput.class),
    DATETIME(DateTimeInput.class),
    DATE(DateInput.class),
    TIME(TimeInput.class),
    DURATION(DurationInput.class),
    FILE(FileInput.class),
    JSON(JsonInput.class),
    ION(IonInput.class),
    URI(URIInput.class),
    SECRET(SecretInput.class),
    ARRAY(ArrayInput.class),
    MULTISELECT(MultiselectInput.class),
    YAML(YamlInput.class),
    EMAIL(EmailInput.class),
    FORM(FormInput.class),
    REUSABLE_INPUTS(ReusableInputsInput.class),
    TABLE(TableInput.class);

    private final Class<? extends Input<?>> cls;

    Type(Class<? extends Input<?>> cls) {
        this.cls = cls;
    }

    public Class<? extends Input<?>> cls() {
        return this.cls;
    }
}
