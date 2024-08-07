package io.kestra.core.runners;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.Plugin;
import io.kestra.core.models.tasks.Task;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RawTaskListDeserializer extends JsonDeserializer<List<Task>> {

    @Override
    public List<Task> deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        RawTaskDeserializer rawTaskDeserializer = new RawTaskDeserializer();
        if (jp.getCurrentToken() == JsonToken.START_ARRAY) {
            final List<Task> list = new ArrayList<>();
            while (jp.nextToken() != JsonToken.END_ARRAY) {
                JsonNode jsonNode = jp.getCodec().readTree(jp);
                list.add(rawTaskDeserializer.deserialize(jp, jsonNode));
            }
            return list;
        }
        throw ctxt.instantiationException(List.class, "Expected list of Tasks");
    }
}
