package io.kestra.plugin.core.kv;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.FlowService;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStoreValueWrapper;
import io.kestra.core.utils.PathMatcherPredicate;
import io.kestra.core.utils.Rethrow;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.commons.nullanalysis.NotNull;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Gets value linked to a key."
)
@Plugin(
    examples = {
        @Example(
            title = "Get value for `myvariable` key in `dev` namespace and fail if it's not present.",
            full = true,
            code = """
              id: get_kv
              type: io.kestra.plugin.core.kv.Get
              key: myvariable
              namespace: dev # the current namespace of the flow will be used by default
              errorOnMissing: true"""
        )
    }
)
public class Get extends Task implements RunnableTask<Get.Output> {
    @NotNull
    @Schema(
        title = "The key for which to get the value."
    )
    @PluginProperty(dynamic = true)
    private String key;

    @NotNull
    @Schema(
        title = "The namespace on which to get the value."
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private String namespace = "{{ flow.namespace }}";

    @NotNull
    @Schema(
        title = "Whether to fail if there is no value for the given key."
    )
    @PluginProperty
    @Builder.Default
    private boolean errorOnMissing = false;


    @Override
    public Output run(RunContext runContext) throws Exception {
        String renderedNamespace = runContext.render(this.namespace);

        FlowService flowService = ((DefaultRunContext) runContext).getApplicationContext().getBean(FlowService.class);
        flowService.checkAllowedNamespace(runContext.tenantId(), renderedNamespace, runContext.tenantId(), runContext.flowInfo().namespace());

        String renderedKey = runContext.render(this.key);

        Optional<Object> maybeValue = runContext.namespaceKv(renderedNamespace).get(renderedKey);
        if (this.errorOnMissing && maybeValue.isEmpty()) {
            throw new NoSuchElementException("No value found for key '" + renderedKey + "' in namespace '" + renderedNamespace + "' and `errorOnMissing` is set to true");
        }

        return Output.builder()
            .value(maybeValue.orElse(null))
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Value retrieve for the key.",
            description = "This can be of any type and will keep the same as when it was set."
        )
        private final Object value;
    }
}
