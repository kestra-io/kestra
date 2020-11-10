package org.kestra.core.docs;

import lombok.*;
import org.kestra.core.plugins.RegisteredPlugin;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class PluginDocumentation {
    private String title;
    private String group;
    private Map<String, Map<String, List<ClassPlugin>>> classPlugins;

    private PluginDocumentation(RegisteredPlugin plugin) {
        this.title = plugin.title();
        this.group = plugin.group();

        this.classPlugins = plugin.allClassGrouped()
            .entrySet()
            .stream()
            .filter(r -> !r.getKey().equals("controllers") && !r.getKey().equals("storages"))
            .flatMap(entry -> entry.getValue()
                .stream()
                .map(cls -> {
                    ClassPlugin.ClassPluginBuilder builder = ClassPlugin.builder()
                        .name(cls.getName())
                        .simpleName(cls.getSimpleName())
                        .type(entry.getKey());

                    if (cls.getPackageName().startsWith(this.group) && cls.getPackageName().length() > this.group.length()) {
                        builder.subgroup(cls.getPackageName().substring(this.group.length() + 1));
                    } else {
                        builder.subgroup("");
                    }

                    return builder.build();
                }))
            .filter(Objects::nonNull)
            .distinct()
            .sorted(Comparator.comparing(ClassPlugin::getSubgroup)
                .thenComparing(ClassPlugin::getType)
                .thenComparing(ClassPlugin::getName)
            )
            .collect(Collectors.groupingBy(
                ClassPlugin::getSubgroup,
                Collectors.groupingBy(ClassPlugin::getType)
            ));
    }

    public static PluginDocumentation of(RegisteredPlugin plugin) {
        return new PluginDocumentation(plugin);
    }

    @AllArgsConstructor
    @Getter
    @Builder
    public static class ClassPlugin {
        String name;
        String simpleName;
        String subgroup;
        String type;
    }
}
