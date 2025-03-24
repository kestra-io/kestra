package io.kestra.plugin.core.kv;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.FlowService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.codehaus.commons.nullanalysis.NotNull;

import java.util.NoSuchElementException;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Deletes a KV pair."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a KV pair.",
            full = true,
            code = """
                id: kv_store_delete
                namespace: company.team

                tasks:
                  - id: kv_delete
                    type: io.kestra.plugin.core.kv.Delete
                    key: my_variable
                    namespace: dev # the current namespace of the flow will be used by default
                """
        )
    }
)
public class Delete extends Task implements RunnableTask<Delete.Output> {
    @NotNull
    @Schema(
        title = "The key for which to delete the value."
    )
    private Property<String> key;

    @NotNull
    @Schema(
        title = "The namespace on which to set the value."
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
        String renderedNamespace = runContext.render(this.namespace).as(String.class).orElseThrow();

        FlowService flowService = ((DefaultRunContext) runContext).getApplicationContext().getBean(FlowService.class);
        flowService.checkAllowedNamespace(runContext.flowInfo().tenantId(), renderedNamespace, runContext.flowInfo().tenantId(), runContext.flowInfo().namespace());

        String renderedKey = runContext.render(this.key).as(String.class).orElseThrow();

        boolean deleted = runContext.namespaceKv(renderedNamespace).delete(renderedKey);
        if (Boolean.TRUE.equals(runContext.render(this.errorOnMissing).as(Boolean.class).orElseThrow()) && !deleted) {
            throw new NoSuchElementException("No value found for key '" + renderedKey + "' in namespace '" + renderedNamespace + "' and `errorOnMissing` is set to true");
        }

        return Output.builder().deleted(deleted).build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Whether the deletion was successful and had a value."
        )
        private final boolean deleted;
    }
}
