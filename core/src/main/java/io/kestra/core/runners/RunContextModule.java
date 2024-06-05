package io.kestra.core.runners;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.kestra.core.plugins.PluginModule;

import java.io.IOException;

public class RunContextModule extends SimpleModule {


    public static final String NAME = "kestra-context";

    /**
     * Creates a new {@link PluginModule} instance.
     */
    public RunContextModule() {
        super(NAME);
        addDeserializer(RunContext.class, new RunContextDeserializer());
    }

    public static class RunContextDeserializer extends JsonDeserializer<RunContext> {

        @Override
        public RunContext deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
            return p.readValueAs(DefaultRunContext.class);
        }
    }
}
