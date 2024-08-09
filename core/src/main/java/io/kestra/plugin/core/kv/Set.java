package io.kestra.plugin.core.kv;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
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

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Sets (or modifies) a KV pair."
)
@Plugin(
    examples = {
        @Example(
            title = "Set `query` task `uri` output as value for `my_variable` key in `dev` namespace.",
            full = true,
            code = """
              id: set_kv
              type: io.kestra.plugin.core.kv.Set
              key: my_variable
              value: "{{ outputs.query.uri }}"
              namespace: dev # the current namespace of the flow will be used by default
              overwrite: true # whether to overwrite or fail if a value for that key already exists; default true
              ttl: P30D # optional TTL"""
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
        title = "The namespace on which to set the value."
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
        title = "Optional time-to-live for the key-value pair."
    )
    @PluginProperty
    private Duration ttl;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String renderedNamespace = runContext.render(this.namespace);

        String renderedKey = runContext.render(this.key);
        Object renderedValue = runContext.renderTyped(this.value);

        KVStore kvStore = runContext.namespaceKv(renderedNamespace);
        kvStore.put(renderedKey, new KVValueAndMetadata(new KVMetadata(ttl), renderedValue), this.overwrite);

        return null;
    }
}
