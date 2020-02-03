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
public class PluginDocumentation<T> {
    @JsonIgnore
    private RegisteredPlugin plugin;

    private Class<? extends T> cls;

    private PluginDocumentation(RegisteredPlugin plugin, Class<T> cls) {
        this.plugin = plugin;
        this.cls = cls;
        this.scan();
    }

    public static <T> PluginDocumentation<T> of(RegisteredPlugin plugin, Class<T> cls) {
        return new PluginDocumentation<>(plugin, cls);
    }

    private String group;

    private String subGroup;

    public String getShortName() {
        return cls.getSimpleName();
    }

    @JsonIgnore
    private Documentation documentation;

    @JsonIgnore
    private List<Example> examples;

    public String getDocDescription() {
        return this.documentation == null ? null : this.documentation.description();
    }

    public String getDocBody() {
        return this.documentation == null ? null : String.join("\n", this.documentation.body());
    }

    public List<ExampleDoc> getDocExamples() {
        return this.examples
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
    }

    private Map<String, InputDocumentation> inputs;

    private Map<String, OutputDocumentation> outputs;

    public void scan() {
        if (this.plugin.getManifest() != null) {
            this.group = this.plugin.getManifest().getMainAttributes().getValue("X-Kestra-Group");
        }

        if (this.group != null && cls.getPackageName().startsWith(this.group) && cls.getPackageName().length() > this.group.length()) {
            this.subGroup = cls.getPackageName().substring(this.group.length() + 1);
        }

        this.documentation = DocumentationGenerator.getClassDoc(cls);
        this.examples = DocumentationGenerator.getClassExample(cls);
        this.inputs = DocumentationGenerator.getMainInputs(cls);
        this.outputs = DocumentationGenerator.getMainOutput(cls);
    }

    @AllArgsConstructor
    @Getter
    public static class ExampleDoc {
        String title;
        String task;
    }
}
