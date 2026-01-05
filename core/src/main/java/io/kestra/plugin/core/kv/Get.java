package io.kestra.plugin.core.kv;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Objects;
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
    title = "Retrieve a value of a KV pair by a key."
)
@Plugin(
    examples = {
        @Example(
            title = "Get value for `my_variable` key in `dev` namespace and fail if it's not present. Note that you can accomplish the same using the `kv()` Pebble function, e.g. `{{kv('my_variable')}}`.",
            full = true,
            code = """
                id: kv_store_get
                namespace: company.team

                tasks:
                  - id: kv_get
                    type: io.kestra.plugin.core.kv.Get
                    key: my_variable
                    namespace: company # the current namespace is used by default
                    errorOnMissing: true
                """
        )
    }
)
public class Get extends Task implements RunnableTask<Get.Output> {
    @NotNull
    @Schema(
        title = "The key for which to get the value"
    )
    private Property<String> key;

    @NotNull
    @Schema(
        title = "The namespace from which to retrieve the KV pair"
    )
    @Builder.Default
    private Property<String> namespace = Property.ofExpression("{{ flow.namespace }}");

    @NotNull
    @Schema(
        title = "Flag specifying whether to fail if there is no value for the given key"
    )
    @Builder.Default
    private Property<Boolean> errorOnMissing = Property.ofValue(false);


    @Override
    public Output run(RunContext runContext) throws Exception {
        String renderedNamespace = runContext.render(this.namespace).as(String.class).orElse(null);
        String flowNamespace = runContext.flowInfo().namespace();
        String renderedKey = runContext.render(this.key).as(String.class).orElse(null);

        Optional<KVValue> value;
        if (Objects.equals(renderedNamespace, flowNamespace)) {
            value = getValueWithInheritance(runContext, flowNamespace, renderedKey);
        } else {
            runContext.acl().allowNamespace(renderedNamespace).check();
            value = runContext.namespaceKv(renderedNamespace).getValue(renderedKey);
        }

        if (Boolean.TRUE.equals(runContext.render(this.errorOnMissing).as(Boolean.class).orElseThrow()) && value.isEmpty()) {
            throw new NoSuchElementException("No value found for key '" + renderedKey + "' in namespace '" + renderedNamespace + "' and `errorOnMissing` is set to true");
        }

        return Output.builder()
            .value(value.map(KVValue::value).orElse(null))
            .build();
    }

    private Optional<KVValue> getValueWithInheritance(RunContext runContext, String flowNamespace, String renderedKey)
            throws IOException, ResourceExpiredException {
        Optional<KVValue> value = Optional.empty();
        String inheritedNamespace = flowNamespace;
        while (value.isEmpty()) {

            value = runContext.namespaceKv(inheritedNamespace).getValue(renderedKey);
            if (!inheritedNamespace.contains(".")){
                return value;
            }
            inheritedNamespace = inheritedNamespace.substring(0, inheritedNamespace.lastIndexOf('.'));
        }
        return value;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Value retrieved for the key",
            description = "This can be of any type and will stay the same as when it was set."
        )
        private final Object value;
    }
}
