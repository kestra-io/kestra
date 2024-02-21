package io.kestra.core.models.flows;

import io.kestra.core.models.flows.input.BooleanInput;
import io.kestra.core.models.flows.input.DateInput;
import io.kestra.core.models.flows.input.DateTimeInput;
import io.kestra.core.models.flows.input.DurationInput;
import io.kestra.core.models.flows.input.FileInput;
import io.kestra.core.models.flows.input.FloatInput;
import io.kestra.core.models.flows.input.IntInput;
import io.kestra.core.models.flows.input.JsonInput;
import io.kestra.core.models.flows.input.SecretInput;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.flows.input.TimeInput;
import io.kestra.core.models.flows.input.URIInput;
import io.micronaut.core.annotation.Introspected;

/**
 * The supported data types.
 */
@Introspected
public enum Type {
    STRING(StringInput.class.getName()),
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
    SECRET(SecretInput.class.getName());

    private final String clsName;

    Type(String clsName) {
        this.clsName = clsName;
    }

    @SuppressWarnings("unchecked")
    public Class<? extends Input<?>> cls() throws ClassNotFoundException {
        return (Class<? extends Input<?>>) Class.forName(this.clsName);
    }
}
