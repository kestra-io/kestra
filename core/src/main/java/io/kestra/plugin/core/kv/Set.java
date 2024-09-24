package io.kestra.plugin.core.kv;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.kv.KVType;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Create or modify a Key-Value pair."
)
@Plugin(
    examples = {
        @Example(
            title = "Set the task's `uri` output as a value for `orders_file` key.",
            full = true,
            code = """
                id: kv_store_set
                namespace: company.team

                tasks:
                  - id: http_download
                    type: io.kestra.plugin.core.http.Download
                    uri: https://huggingface.co/datasets/kestra/datasets/raw/main/csv/orders.csv

                  - id: kv_set
                    type: io.kestra.plugin.core.kv.Set
                    key: orders_file
                    value: "{{ outputs.http_download.uri }}"
                    kvType: STRING
                """
        )
    }
)
public class Set extends Task implements RunnableTask<VoidOutput> {
    @NotNull
    @Schema(
        title = "The key for which to set the value."
    )
    @PluginProperty(dynamic = true)
    private String key;

    @NotNull
    @Schema(
        title = "The value to map to the key."
    )
    @PluginProperty(dynamic = true)
    private String value;

    @NotNull
    @Schema(
        title = "The namespace in which the KV pair will be stored. By default, Kestra will use the namespace of the flow."
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private String namespace = "{{ flow.namespace }}";

    @NotNull
    @Schema(
        title = "Whether to overwrite or fail if a value for the given key already exists."
    )
    @PluginProperty
    @Builder.Default
    private boolean overwrite = true;

    @Schema(
        title = "Optional Time-To-Live (TTL) duration for the key-value pair. If not set, the KV pair will never be deleted from internal storage."
    )
    @PluginProperty
    private Duration ttl;

    @Schema(
        title = "Enum representing the data type of the KV pair. If not set, the value will be stored as a string."
    )
    @PluginProperty
    private KVType kvType;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String renderedNamespace = runContext.render(this.namespace);

        String renderedKey = runContext.render(this.key);
        Object renderedValue = runContext.renderTyped(this.value);

        KVStore kvStore = runContext.namespaceKv(renderedNamespace);

        if (kvType != null) {
            if (renderedValue instanceof String renderedValueStr) {
                renderedValue = switch (kvType) {
                    case NUMBER -> JacksonMapper.ofJson().readValue(renderedValueStr, Number.class);
                    case BOOLEAN -> Boolean.parseBoolean((String) renderedValue);
                    case DATETIME, DATE -> Instant.parse(renderedValueStr);
                    case DURATION -> Duration.parse(renderedValueStr);
                    case JSON -> JacksonMapper.toObject(renderedValueStr);
                    default -> renderedValue;
                };
            }
        }
        kvStore.put(renderedKey, new KVValueAndMetadata(new KVMetadata(ttl), renderedValue), this.overwrite);

        return null;
    }
}
