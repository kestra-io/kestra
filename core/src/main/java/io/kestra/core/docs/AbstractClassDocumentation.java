package io.kestra.core.docs;

import com.google.common.base.CaseFormat;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("this-escape")
@Getter
@EqualsAndHashCode
@ToString
@Slf4j
public abstract class AbstractClassDocumentation<T> {
    protected Boolean deprecated;
    protected Boolean beta;
    protected String cls;
    protected String shortName;
    protected String docDescription;
    protected String docBody;
    protected List<ExampleDoc> docExamples;
    protected Map<String, Object> defs = new TreeMap<>();
    protected Map<String, Object> inputs = new TreeMap<>();
    protected Map<String, Object> propertiesSchema;
    private final List<String> defsExclusions = List.of(
        "io.kestra.core.models.conditions.Condition",
        "io.kestra.core.models.conditions.ScheduleCondition"
    );

    @SuppressWarnings("unchecked")
    protected AbstractClassDocumentation(JsonSchemaGenerator jsonSchemaGenerator, Class<? extends T> cls, Class<T> baseCls) {
        this.cls = cls.getName();
        this.shortName = cls.getSimpleName();

        this.propertiesSchema = jsonSchemaGenerator.properties(baseCls, cls);

        if (this.propertiesSchema.containsKey("$defs")) {
            this.defs.putAll((Map<String, Object>) this.propertiesSchema.get("$defs"));
            defsExclusions.forEach(this.defs::remove);
            this.propertiesSchema.remove("$defs");
        }

        // add $required on defs
        this.defs = this.getDefs()
            .entrySet()
            .stream()
            // Remove the Task entry as it only contains a reference that is filtered in the doc template,
            // which prevent the Definitions section to be empty if no other def exist.
            .filter(entry -> !entry.getKey().equals("io.kestra.core.models.tasks.Task"))
            .map(entry -> {
                Map<String, Object> value = (Map<String, Object>) entry.getValue();
                value.put("properties", flatten(properties(value), required(value), isTypeToKeep(entry.getKey())));

                return new AbstractMap.SimpleEntry<>(
                    entry.getKey(),
                    value
                );
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        this.docDescription = this.propertiesSchema.containsKey("title") ? (String) this.propertiesSchema.get("title") : null;
        this.docBody = this.propertiesSchema.containsKey("description") ? (String) this.propertiesSchema.get("description") : null;
        this.deprecated = this.propertiesSchema.containsKey("$deprecated");
        this.beta = this.propertiesSchema.containsKey("$beta");

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
                .toList();
        }

        if (this.propertiesSchema.containsKey("properties")) {
            this.inputs = flatten(properties(this.propertiesSchema), required(this.propertiesSchema));
        }
    }

    protected static Map<String, Object> flatten(Map<String, Object> map, List<String> required) {
        map.remove("type");
        return flatten(map, required, (String) null);
    }

    protected static Map<String, Object> flatten(Map<String, Object> map, List<String> required, Boolean keepType) {
        if (!keepType) {
            map.remove("type");
        }
        return flatten(map, required, (String) null);
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
            } else {
                finalValue.put("$required", false);
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

    // Some task can have the `type` property but not to represent the task
    // so we cant to keep it in the doc
    private Boolean isTypeToKeep(String key){
        try {
            if (AbstractRetry.class.isAssignableFrom(Class.forName(key))) {
                return true;
            }
        } catch (ClassNotFoundException ignored) {
            log.debug(ignored.getMessage(), ignored);
        }
        return false;
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
