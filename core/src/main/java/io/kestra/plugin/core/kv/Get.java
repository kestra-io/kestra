package io.kestra.plugin.core.kv;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.FlowService;
import io.kestra.core.storages.kv.KVValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.NoSuchElementException;
import java.util.Optional;

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
            title = "Get value for `my_variable` key in `dev` namespace and fail if it's not present.",
            full = true,
            code = """
                id: kv_store_get
                namespace: company.team

                tasks:
                  - id: kv_get
                    type: io.kestra.plugin.core.kv.Get
                    key: my_variable
                    namespace: dev # the current namespace of the flow will be used by default
                    errorOnMissing: true
                """
        )
    }
)
public class Get extends Task implements RunnableTask<Get.Output> {
    @NotNull
    @Schema(
        title = "The key for which to get the value."
    )
    private Property<String> key;

    @NotNull
    @Schema(
        title = "The namespace on which to get the value."
    )
    @Builder.Default
    private Property<String> namespace = new Property<>("{{ flow.namespace }}");

    @NotNull
    @Schema(
        title = "Whether to fail if there is no value for the given key."
    )
    @Builder.Default
    private Property<Boolean> errorOnMissing = Property.of(false);


    @Override
    public Output run(RunContext runContext) throws Exception {
        String renderedNamespace = runContext.render(this.namespace).as(String.class).orElse(null);

        FlowService flowService = ((DefaultRunContext) runContext).getApplicationContext().getBean(FlowService.class);
        flowService.checkAllowedNamespace(runContext.flowInfo().tenantId(), renderedNamespace, runContext.flowInfo().tenantId(), runContext.flowInfo().namespace());

        String renderedKey = runContext.render(this.key).as(String.class).orElse(null);

        Optional<KVValue> maybeValue = runContext.namespaceKv(renderedNamespace).getValue(renderedKey);
        if (Boolean.TRUE.equals(runContext.render(this.errorOnMissing).as(Boolean.class).orElseThrow()) && maybeValue.isEmpty()) {
            throw new NoSuchElementException("No value found for key '" + renderedKey + "' in namespace '" + renderedNamespace + "' and `errorOnMissing` is set to true");
        }

        return Output.builder()
            .value(maybeValue.map(KVValue::value).orElse(null))
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
