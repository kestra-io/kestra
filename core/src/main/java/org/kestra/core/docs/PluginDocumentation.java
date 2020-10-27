package org.kestra.core.docs;

import com.google.common.base.CaseFormat;
import lombok.*;
import org.apache.commons.lang3.ArrayUtils;
import org.kestra.core.plugins.RegisteredPlugin;

import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class PluginDocumentation<T> {
    private static final JsonSchemaGenerator JSON_SCHEMA_GENERATOR = new JsonSchemaGenerator();

    private String cls;
    private String group;
    private String subGroup;
    private String shortName;
    private String docDescription;
    private String docBody;
    private List<ExampleDoc> docExamples;
    private Map<String, Object> inputs = new TreeMap<>();
    private Map<String, Object> outputs = new TreeMap<>();
    private Map<String, Object> propertiesSchema;
    private Map<String, Object> outputsSchema;


    @SuppressWarnings("unchecked")
    private PluginDocumentation(RegisteredPlugin plugin, Class<? extends T> cls, Class<T> baseCls) {
        this.cls = cls.getName();

        if (plugin.getManifest() != null) {
            this.group = plugin.getManifest().getMainAttributes().getValue("X-Kestra-Group");
        }

        if (this.group != null && cls.getPackageName().startsWith(this.group) && cls.getPackageName().length() > this.group.length()) {
            this.subGroup = cls.getPackageName().substring(this.group.length() + 1);
        }

        this.shortName = cls.getSimpleName();

        this.propertiesSchema = JSON_SCHEMA_GENERATOR.properties(baseCls, cls);
        this.outputsSchema = JSON_SCHEMA_GENERATOR.outputs(baseCls, cls);

        this.docDescription = this.propertiesSchema.containsKey("title") ? (String) this.propertiesSchema.get("title") : null;;
        this.docBody = this.propertiesSchema.containsKey("description") ? (String) this.propertiesSchema.get("description") : null;

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

        if (this.propertiesSchema.containsKey("properties")) {
            this.inputs = flatten(properties(this.propertiesSchema), required(this.propertiesSchema));
        }

        if (this.outputsSchema.containsKey("properties")) {
            this.outputs = flatten(properties(this.outputsSchema), required(this.outputsSchema));
        }
    }

    private static Map<String, Object> flatten(Map<String, Object> map, List<String> required) {
        map.remove("type");
        return flatten(map, required, null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> flatten(Map<String, Object> map, List<String> required, String parentName) {
        Map<String, Object> result = new TreeMap<>();

        for (Map.Entry<String, Object> current : map.entrySet()) {
            Map<String, Object> finalValue = (Map<String, Object>) current.getValue();
            if (required.contains(current.getKey())) {
                finalValue.put("$required", true);
            }

            result.put(flattenKey(current.getKey(), parentName), finalValue);
            if (current.getValue() instanceof Map) {
                Map<String, Object> value = (Map<String, Object>) current.getValue();

                if (value.containsKey("properties")) {
                    result.putAll(flatten(properties(value), required(value), current.getKey()));
                }
            }
        }

        return result;
    }

    private static String flattenKey(String current, String parent) {
        return (parent != null ? parent + "." : "") + current;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> props) {
        return (Map<String, Object>) props.get("properties");
    }

    @SuppressWarnings("unchecked")
    private static List<String> required(Map<String, Object> props) {
        if (!props.containsKey("required")) {
            return Collections.emptyList();
        }

        return (List<String>) props.get("required");
    }

    public static <T> PluginDocumentation<T> of(RegisteredPlugin plugin, Class<? extends T> cls, Class<T> baseCls) {
        return new PluginDocumentation<>(plugin, cls, baseCls);
    }

    @AllArgsConstructor
    @Getter
    public static class ExampleDoc {
        String title;
        String task;
    }
}
