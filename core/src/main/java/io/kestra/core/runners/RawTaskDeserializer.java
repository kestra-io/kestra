package io.kestra.core.runners;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.Plugin;
import io.kestra.core.models.tasks.Task;

import java.io.IOException;

public class RawTaskDeserializer extends JsonDeserializer<Task> {

    @Override
    public Task deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonNode jsonNode = jp.readValueAsTree();
        return deserialize(jp, jsonNode);
    }

    public Task deserialize(JsonParser jp, JsonNode jsonNode) throws IOException {
        // TODO refactor with PluginDeserializer
        // Get task type
        JsonNode type = jsonNode.get("type");
        if (type == null || type.textValue().isEmpty()) {
            return null;
        }
        String typeIdentifier = type.textValue();

        // Check whether plugin can be pre-rendered (this could be managed )
        Class<? extends Plugin> pluginClass = KestraContext.getContext().getPluginRegistry()
            .findClassByIdentifier(typeIdentifier);

        io.kestra.core.models.annotations.Plugin annotation = pluginClass
            .getAnnotation(io.kestra.core.models.annotations.Plugin.class);

        if (annotation.enableAutoPropertiesDynamicRendering()) {
            // deserialize to the raw Task
            return jp.getCodec().treeToValue(jsonNode, RawTask.class);
        } else {
            // deserialize directly to the target Task
            return jp.getCodec().treeToValue(jsonNode, Task.class);
        }
    }
}
