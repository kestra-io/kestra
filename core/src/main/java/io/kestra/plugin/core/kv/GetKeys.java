package io.kestra.plugin.core.kv;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.FlowService;
import io.kestra.core.storages.kv.KVEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.commons.nullanalysis.NotNull;

import java.util.*;

@Slf4j
@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Gets keys matching a given prefix."
)
@Plugin(
    examples = {
        @Example(
            title = "Get keys that are prefixed by `myvar`.",
            code = """
              id: keys_kv
              type: io.kestra.plugin.core.kv.GetKeys
              prefix: myvar
              namespace: dev # the current namespace of the flow will be used by default"""
        )
    }
)
public class GetKeys extends Task implements RunnableTask<GetKeys.Output> {
    @Schema(
        title = "The key for which to get the value."
    )
    @PluginProperty(dynamic = true)
    private String prefix;

    @NotNull
    @Schema(
        title = "The namespace on which to get the value."
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private String namespace = "{{ flow.namespace }}";


    @Override
    public Output run(RunContext runContext) throws Exception {
        String renderedNamespace = runContext.render(this.namespace);

        FlowService flowService = ((DefaultRunContext) runContext).getApplicationContext().getBean(FlowService.class);
        flowService.checkAllowedNamespace(runContext.tenantId(), renderedNamespace, runContext.tenantId(), runContext.flowInfo().namespace());

        String renderedPrefix = runContext.render(this.prefix);

        List<String> keys = runContext.storage().namespaceKv(renderedNamespace).list().stream()
            .map(KVEntry::key)
            .filter(key -> key.startsWith(renderedPrefix))
            .toList();

        return Output.builder()
            .keys(keys)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Found keys for given prefix."
        )
        private final List<String> keys;
    }
}
