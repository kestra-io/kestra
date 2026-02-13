package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.io.Serial;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom serializer for {@link RunContext}.
 * <p>
 * Filters out the "envs" key from the variables map during serialization ensuring that environment
 * variables are not sent to workers.
 */
public class RunContextSerializer extends StdSerializer<RunContext> {

    @Serial
    private static final long serialVersionUID = 1L;

    protected RunContextSerializer() {
        super(RunContext.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(RunContext value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        Map<String, Object> mutableVariables = new HashMap<>(value.getVariables());
        mutableVariables.remove("envs");
        gen.writeObject(new RunContextData(mutableVariables, value.getSecretInputs(), value.getTraceParent()));
    }

    /**
     * Intermediate record used to preserve null values during serialization.
     * <p>
     * The {@code @JsonInclude} annotation (defaulting to {@code ALWAYS} for both value and content)
     * overrides the mapper-level {@code NON_NULL} policy, matching the behavior of {@link RunContext}'s property annotations.
     */
    private record RunContextData(
        @JsonInclude
        Map<String, Object> variables,
        @JsonInclude
        List<String> secretInputs,
        @JsonInclude
        String traceParent
    ) {}
}
