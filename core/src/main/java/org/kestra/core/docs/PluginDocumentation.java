package org.kestra.core.docs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.CaseFormat;
import lombok.*;
import org.apache.commons.lang3.ArrayUtils;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.plugins.RegisteredPlugin;

import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@Builder
@NoArgsConstructor
public class PluginDocumentation<T> {
    private String cls;
    private String group;
    private String subGroup;
    private String shortName;
    private String docDescription;
    private String docBody;
    private List<ExampleDoc> docExamples;
    private Map<String, InputDocumentation> inputs;
    private Map<String, OutputDocumentation> outputs;

    private PluginDocumentation(RegisteredPlugin plugin, Class<T> cls) {
        this.cls = cls.getName();

        if (plugin.getManifest() != null) {
            this.group = plugin.getManifest().getMainAttributes().getValue("X-Kestra-Group");
        }

        if (this.group != null && cls.getPackageName().startsWith(this.group) && cls.getPackageName().length() > this.group.length()) {
            this.subGroup = cls.getPackageName().substring(this.group.length() + 1);
        }

        this.shortName = cls.getSimpleName();

        Documentation classDoc = DocumentationGenerator.getClassDoc(cls);
        this.docDescription = classDoc == null ? null : classDoc.description();;
        this.docBody = classDoc == null ? null : String.join("\n", classDoc.body());

        List<Example> classExample = DocumentationGenerator.getClassExample(cls);
        this.docExamples = classExample
            .stream()
            .map(r -> new ExampleDoc(
                r.title(),
                String.join("\n", ArrayUtils.addAll(
                    (r.full() ? new ArrayList<String>() : Arrays.asList(
                        "id: \"" + CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, cls.getSimpleName()) + "\"",
                        "type: \"" + cls.getName() + "\""
                    )).toArray(new String[0]),
                    r.code()
                ))
            ))
            .collect(Collectors.toList());

        this.inputs = DocumentationGenerator.getMainInputs(cls);
        this.outputs = DocumentationGenerator.getMainOutput(cls);
    }

    public static <T> PluginDocumentation<T> of(RegisteredPlugin plugin, Class<T> cls) {
        return new PluginDocumentation<>(plugin, cls);
    }

    @AllArgsConstructor
    @Getter
    public static class ExampleDoc {
        String title;
        String task;
    }
}
