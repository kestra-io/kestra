package io.kestra.core.docs;

import io.kestra.core.plugins.RegisteredPlugin;
import lombok.*;

import java.util.*;
import java.util.stream.Collectors;

@Getter
@EqualsAndHashCode
@ToString
public class ClassPluginDocumentation<T> extends AbstractClassDocumentation<T> {
    private String icon;
    private String group;
    private String pluginTitle;
    private String subGroup;
    private List<MetricDoc> docMetrics;
    private Map<String, Object> outputs = new TreeMap<>();
    private Map<String, Object> outputsSchema;

    @SuppressWarnings("unchecked")
    private ClassPluginDocumentation(JsonSchemaGenerator jsonSchemaGenerator, RegisteredPlugin plugin, Class<? extends T> cls, Class<T> baseCls, String alias) {
        super(jsonSchemaGenerator, cls, baseCls);

        // plugins metadata
        this.cls = alias == null ? cls.getName() : alias;
        this.group = plugin.group();
        this.pluginTitle = plugin.title();
        this.icon = plugin.icon(cls);

        if (this.group != null && cls.getPackageName().startsWith(this.group) && cls.getPackageName().length() > this.group.length() && cls.getPackageName().charAt(this.group.length()) == '.') {
            this.subGroup = cls.getPackageName().substring(this.group.length() + 1);
        }

        this.shortName = cls.getSimpleName();

        // outputs
        this.outputsSchema = jsonSchemaGenerator.outputs(baseCls, cls);

        if (this.outputsSchema.containsKey("$defs")) {
            this.defs.putAll((Map<String, Object>) this.outputsSchema.get("$defs"));
            this.outputsSchema.remove("$defs");
        }

        if (this.outputsSchema.containsKey("properties")) {
            this.outputs = flatten(properties(this.outputsSchema), required(this.outputsSchema));
        }

        // metrics
        if (this.propertiesSchema.containsKey("$metrics")) {
            List<Map<String, Object>> metrics = (List<Map<String, Object>>) this.propertiesSchema.get("$metrics");

            this.docMetrics = metrics
                .stream()
                .map(r -> new MetricDoc(
                    (String) r.get("name"),
                    (String) r.get("type"),
                    (String) r.get("unit"),
                    (String) r.get("description")
                ))
                .collect(Collectors.toList());
        }

        if (alias != null) {
            this.deprecated = true;
        }
    }

    public static <T> ClassPluginDocumentation<T> of(JsonSchemaGenerator jsonSchemaGenerator, RegisteredPlugin plugin, Class<? extends T> cls, Class<T> baseCls) {
        return new ClassPluginDocumentation<>(jsonSchemaGenerator, plugin, cls, baseCls, null);
    }

    public static <T> ClassPluginDocumentation<T> of(JsonSchemaGenerator jsonSchemaGenerator, RegisteredPlugin plugin, Class<? extends T> cls, Class<T> baseCls, String alias) {
        return new ClassPluginDocumentation<>(jsonSchemaGenerator, plugin, cls, baseCls, alias);
    }

    @AllArgsConstructor
    @Getter
    public static class MetricDoc {
        String name;
        String type;
        String unit;
        String description;
    }
}

