package io.kestra.plugin.core.kv;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.MapUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Update an existing Key-Value entry",
    description = """
        Renders `key`, `value`, and `namespace` (defaults to the Flow namespace), then writes to the namespace KV store.

        Map values are deep merged (nested object keys are preserved); keyed entries like `[{key: ..., value: ...}]` update JSON fields (dotted paths allowed) while `[{value: ...}]` replaces the entire value. Set `errorOnMissing` to `true` to fail when the key is absent, otherwise a new key-value pair is created.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Replace a string value",
            full = true,
            code = """
                id: kv_put_string
                namespace: company.team

                tasks:
                  - id: update_my_string
                    type: io.kestra.plugin.core.kv.Put
                    key: my-string-key
                    value: "new-value"
                """
        ),
        @Example(
            title = "Insert or update JSON keys",
            full = true,
            code = """
                id: kv_put_json
                namespace: company.team

                tasks:
                  - id: update_my_json
                    type: io.kestra.plugin.core.kv.Put
                    key: my-json-key
                    value:
                      - key: "json-key-1"
                        value: "my new value"
                      - key: "nested.field"
                        value: 123
                """
        )
    }
)
public class Put extends Task implements RunnableTask<VoidOutput> {
    @NotNull
    @Schema(
        title = "Key to update",
        description = "Key name within the target namespace"
    )
    private Property<String> key;

    @NotNull
    @Schema(
        title = "Value payload to write",
        description = "Maps are deep merged and entry lists can replace or patch JSON fields"
    )
    private Property<Object> value;

    @NotNull
    @Schema(
        title = "Target namespace",
        description = "Defaults to the current Flow namespace; accepts expressions"
    )
    @Builder.Default
    private Property<String> namespace = Property.ofExpression("{{ flow.namespace }}");

    @Schema(
        title = "Fail when key is absent",
        description = "Default `false`; when `true` throws before writing if the key does not exist"
    )
    @Builder.Default
    private Property<Boolean> errorOnMissing = Property.ofValue(false);

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var rNamespace = runContext.render(this.namespace).as(String.class).orElseThrow();
        var rKey = runContext.render(this.key).as(String.class).orElseThrow();
        var rValue = runContext.render(this.value).as(Object.class).orElse(null);
        if (rValue instanceof String renderedValueAsString) {
            rValue = runContext.renderTyped(renderedValueAsString);
        }

        var kvStore = runContext.namespaceKv(rNamespace);
        var current = kvStore.findMetadataAndValue(rKey);

        if (runContext.render(this.errorOnMissing).as(Boolean.class).orElseThrow() && current.isEmpty()) {
            throw new NoSuchElementException("No value found for key '" + rKey + "' in namespace '" + rNamespace + "' and `errorOnMissing` is set to true");
        }

        var mergedValue = this.mergeValues(current.map(KVValueAndMetadata::value).orElse(null), rValue);
        var metadata = current.map(KVValueAndMetadata::metadata).orElse(null);

        kvStore.put(rKey, new KVValueAndMetadata(metadata, mergedValue), true);
        return null;
    }

    private Object mergeValues(Object existingValue, Object newValue) {
        if (newValue instanceof List<?> entries && this.isValueEntryList(entries)) {
            return this.mergeEntryListValues(existingValue, entries);
        }

        if (existingValue instanceof Map<?, ?> existingMap && newValue instanceof Map<?, ?> newMap) {
            return MapUtils.deepMerge(this.toStringKeyMap(existingMap, "existing value"), this.toStringKeyMap(newMap, "value"));
        }

        return newValue;
    }

    private Object mergeEntryListValues(Object existingValue, List<?> entries) {
        Object replaceValue = null;
        var hasReplaceValue = false;
        Map<String, Object> updates = new HashMap<>();

        for (var entryObj : entries) {
            var entry = (Map<?, ?>) entryObj;
            this.validateEntry(entry);

            var key = entry.get("key");
            var value = entry.get("value");

            if (key == null) {
                if (hasReplaceValue || !updates.isEmpty() || entries.size() > 1) {
                    throw new IllegalArgumentException("`value` entries without `key` can only be used as a single replacement item.");
                }
                hasReplaceValue = true;
                replaceValue = value;
            } else {
                if (hasReplaceValue) {
                    throw new IllegalArgumentException("Cannot mix keyed and non-keyed entries in `value`.");
                }
                if (!(key instanceof String keyAsString) || keyAsString.isBlank()) {
                    throw new IllegalArgumentException("Each keyed `value` entry must have a non-empty string `key`.");
                }
                updates.put(keyAsString, value);
            }
        }

        if (hasReplaceValue) {
            return replaceValue;
        }

        var nestedUpdates = MapUtils.flattenToNestedMap(updates);
        if (existingValue == null) {
            return nestedUpdates;
        }

        if (!(existingValue instanceof Map<?, ?> existingMap)) {
            throw new IllegalArgumentException("Cannot apply keyed `value` updates to a non-JSON existing KV value.");
        }

        return MapUtils.deepMerge(this.toStringKeyMap(existingMap, "existing value"), nestedUpdates);
    }

    private boolean isValueEntryList(List<?> entries) {
        if (entries.isEmpty()) {
            return false;
        }

        return entries.stream().allMatch(item ->
            item instanceof Map<?, ?> map &&
                map.containsKey("value") &&
                map.keySet().stream().allMatch(fieldName -> "key".equals(fieldName) || "value".equals(fieldName))
        );
    }

    private void validateEntry(Map<?, ?> entry) {
        if (!entry.containsKey("value")) {
            throw new IllegalArgumentException("Each `value` entry must contain a `value` field.");
        }

        for (var fieldName : entry.keySet()) {
            if (!"key".equals(fieldName) && !"value".equals(fieldName)) {
                throw new IllegalArgumentException("Invalid field '" + fieldName + "' in `value` entry. Allowed fields are `key` and `value`.");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toStringKeyMap(Map<?, ?> map, String fieldName) {
        if (!map.keySet().stream().allMatch(String.class::isInstance)) {
            throw new IllegalArgumentException("`" + fieldName + "` map keys must be strings.");
        }
        return (Map<String, Object>) map;
    }
}
