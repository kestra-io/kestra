package io.kestra.core.docs;

import com.google.common.base.CaseFormat;
import lombok.*;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;
import java.util.stream.Collectors;

@Getter
@EqualsAndHashCode
@ToString
public abstract class AbstractClassDocumentation<T> {
    protected Boolean deprecated;
    protected String cls;
    protected String shortName;
    protected String docDescription;
    protected String docBody;
    protected List<ExampleDoc> docExamples;
    protected Map<String, Object> defs = new TreeMap<>();
    protected Map<String, Object> inputs = new TreeMap<>();
    protected Map<String, Object> propertiesSchema;

    @SuppressWarnings("unchecked")
    protected AbstractClassDocumentation(JsonSchemaGenerator jsonSchemaGenerator, Class<? extends T> cls, Class<T> baseCls) {
        this.cls = cls.getName();
        this.shortName = cls.getSimpleName();

        this.propertiesSchema = jsonSchemaGenerator.properties(baseCls, cls);

        if (this.propertiesSchema.containsKey("$defs")) {
            this.defs.putAll((Map<String, Object>) this.propertiesSchema.get("$defs"));
            this.propertiesSchema.remove("$defs");
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

        if (this.propertiesSchema.containsKey("properties")) {
            this.inputs = flatten(properties(this.propertiesSchema), required(this.propertiesSchema));
        }
    }

    protected static Map<String, Object> flatten(Map<String, Object> map, List<String> required) {
        map.remove("type");
        return flatten(map, required, null);
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> flatten(Map<String, Object> map, List<String> required, String parentName) {
        Map<String, Object> result = new TreeMap<>((key1, key2) -> {
            boolean key1Required = required.contains(key1);
            boolean key2Required = required.contains(key2);
            if (key1Required == key2Required) {
                return key1.compareTo(key2);
            }

            return key1Required ? -1 : 1;
        });

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

    protected static String flattenKey(String current, String parent) {
        return (parent != null ? parent + "." : "") + current;
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> properties(Map<String, Object> props) {
        Map<String, Object> properties = (Map<String, Object>) props.get("properties");

        return properties != null ? properties : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    protected static List<String> required(Map<String, Object> props) {
        if (!props.containsKey("required")) {
            return Collections.emptyList();
        }

        return (List<String>) props.get("required");
    }

    @AllArgsConstructor
    @Getter
    public static class ExampleDoc {
        String title;
        String task;
    }
}
