package io.kestra.core.docs;

import com.google.common.base.CaseFormat;
import io.kestra.core.models.flows.Input;
import lombok.*;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class ClassInputDocumentation<T> extends ClassDocumentation {
    private Boolean deprecated;
    private String cls;
    private String shortName;
    private String docDescription;
    private String docBody;
    private List<ExampleDoc> docExamples;
    private Map<String, Object> defs = new TreeMap<>();
    private Map<String, Object> inputs = new TreeMap<>();
    private Map<String, Object> propertiesSchema;


    @SuppressWarnings("unchecked")
    private ClassInputDocumentation(JsonSchemaGenerator jsonSchemaGenerator, Class<? extends Input<?>> cls) {
        this.cls = cls.getName();
        this.shortName = cls.getSimpleName();

        this.propertiesSchema = jsonSchemaGenerator.properties(Input.class, cls);

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

    public static <T> ClassInputDocumentation<T> of(JsonSchemaGenerator jsonSchemaGenerator, Class<? extends Input<?>> cls) {
        return new ClassInputDocumentation<>(jsonSchemaGenerator, cls);
    }
}
