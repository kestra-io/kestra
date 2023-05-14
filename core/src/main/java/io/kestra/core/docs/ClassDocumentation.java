package io.kestra.core.docs;

import lombok.*;

import java.util.*;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class ClassDocumentation {
    protected Boolean deprecated;
    protected String cls;
    protected String shortName;
    protected String docDescription;
    protected String docBody;
    protected List<ExampleDoc> docExamples;
    protected Map<String, Object> defs = new TreeMap<>();
    protected Map<String, Object> inputs = new TreeMap<>();
    protected Map<String, Object> propertiesSchema;

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
