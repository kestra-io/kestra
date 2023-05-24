package io.kestra.core.docs;

import com.google.common.base.CaseFormat;
import io.kestra.core.plugins.RegisteredPlugin;
import lombok.*;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class ClassPluginDocumentation<T> extends ClassDocumentation {
    private Boolean deprecated;
    private String cls;
    private String icon;
    private String group;
    private String pluginTitle;
    private String subGroup;
    private String shortName;
    private String docDescription;
    private String docBody;
    private List<ExampleDoc> docExamples;
    private List<MetricDoc> docMetrics;
    private Map<String, Object> defs = new TreeMap<>();
    private Map<String, Object> inputs = new TreeMap<>();
    private Map<String, Object> outputs = new TreeMap<>();
    private Map<String, Object> propertiesSchema;
    private Map<String, Object> outputsSchema;


    @SuppressWarnings("unchecked")
    private ClassPluginDocumentation(JsonSchemaGenerator jsonSchemaGenerator, RegisteredPlugin plugin, Class<? extends T> cls, Class<T> baseCls) {
        this.cls = cls.getName();
        this.group = plugin.group();
        this.pluginTitle = plugin.title();
        this.icon = DocumentationGenerator.icon(plugin, cls);

        if (this.group != null && cls.getPackageName().startsWith(this.group) && cls.getPackageName().length() > this.group.length() && cls.getPackageName().charAt(this.group.length()) == '.') {
            this.subGroup = cls.getPackageName().substring(this.group.length() + 1);
        }

        this.shortName = cls.getSimpleName();

        this.propertiesSchema = jsonSchemaGenerator.properties(baseCls, cls);
        this.outputsSchema = jsonSchemaGenerator.outputs(baseCls, cls);

        if (this.propertiesSchema.containsKey("$defs")) {
            this.defs.putAll((Map<String, Object>) this.propertiesSchema.get("$defs"));
            this.propertiesSchema.remove("$defs");
        }

        if (this.outputsSchema.containsKey("$defs")) {
            this.defs.putAll((Map<String, Object>) this.outputsSchema.get("$defs"));
            this.outputsSchema.remove("$defs");
        }

        // add $required on defs
        this.defs = this.getDefs()
            .entrySet()
            .stream()
            .map(entry -> {
                Map<String, Object> value = (Map<String, Object>) entry.getValue();
                value.put("properties", flatten(properties(value), required(value)));

                return new AbstractMap.SimpleEntry<>(
                    entry.getKey(),
                    value
                );
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        this.docDescription = this.propertiesSchema.containsKey("title") ? (String) this.propertiesSchema.get("title") : null;
        this.docBody = this.propertiesSchema.containsKey("description") ? (String) this.propertiesSchema.get("description") : null;
        this.deprecated = this.propertiesSchema.containsKey("$deprecated");

        if (this.propertiesSchema.containsKey("$examples")) {
            List<Map<String, Object>> examples = (List<Map<String, Object>>) this.propertiesSchema.get("$examples");

            this.docExamples = examples
                .stream()
                .map(r -> new ExampleDoc(
                    (String) r.get("title"),
                    String.join("\n", ArrayUtils.addAll(
                        ((Boolean) r.get("full") ? new ArrayList<String>() : Arrays.asList(
                            "id: \"" + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, cls.getSimpleName()) + "\"",
                            "type: \"" + cls.getName() + "\""
                        )).toArray(new String[0]),
                        (String) r.get("code")
                    ))
                ))
                .collect(Collectors.toList());
        }

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

        if (this.propertiesSchema.containsKey("properties")) {
            this.inputs = flatten(properties(this.propertiesSchema), required(this.propertiesSchema));
        }

        if (this.outputsSchema.containsKey("properties")) {
            this.outputs = flatten(properties(this.outputsSchema), required(this.outputsSchema));
        }
    }

    public static <T> ClassPluginDocumentation<T> of(JsonSchemaGenerator jsonSchemaGenerator, RegisteredPlugin plugin, Class<? extends T> cls, Class<T> baseCls) {
        return new ClassPluginDocumentation<>(jsonSchemaGenerator, plugin, cls, baseCls);
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

