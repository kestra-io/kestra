package io.kestra.plugin.core.kv;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Predicate;

@Slf4j
@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Fetch all keys matching a given KV pair prefix."
)
@Plugin(
    examples = {
        @Example(
            title = "Get keys that are prefixed by `my_var`.",
            full = true,
            code = """
                id: kv_store_getkeys
                namespace: company.team

                tasks:
                  - id: kv_getkeys
                    type: io.kestra.plugin.core.kv.GetKeys
                    prefix: my_var
                    namespace: dev # the current namespace of the flow will be used by default
                """
        )
    }
)
public class GetKeys extends Task implements RunnableTask<GetKeys.Output> {
    @Schema(
        title = "The key for which to get the values"
    )
    private Property<String> prefix;

    @NotNull
    @Schema(
        title = "The namespace from which to get the KV pairs"
    )
    @Builder.Default
    private Property<String> namespace = Property.ofExpression("{{ flow.namespace }}");


    @Override
    public Output run(RunContext runContext) throws Exception {
        String renderedNamespace = runContext.render(this.namespace).as(String.class).orElse(null);
        runContext.acl().allowNamespace(renderedNamespace).check();

        String renderedPrefix = runContext.render(this.prefix).as(String.class).orElse(null);
        Predicate<String> filter = renderedPrefix == null ? key -> true : key -> key.startsWith(renderedPrefix);

        List<String> keys = runContext.namespaceKv(renderedNamespace).list().stream()
            .map(KVEntry::key)
            .filter(filter)
            .toList();

        return Output.builder()
            .keys(keys)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Found keys for given prefix"
        )
        private final List<String> keys;
    }
}
