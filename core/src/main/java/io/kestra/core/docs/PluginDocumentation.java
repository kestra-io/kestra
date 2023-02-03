package io.kestra.core.docs;

import io.kestra.core.models.annotations.PluginSubGroup;
import lombok.*;
import io.kestra.core.plugins.RegisteredPlugin;
import lombok.extern.jackson.Jacksonized;

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
    private Map<SubGroup, Map<String, List<ClassPlugin>>> classPlugins;

    private Map<String, String> guides;

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
                    if (cls.getPackageName().startsWith(this.group)) {
                        var pluginSubGroup = cls.getPackage().getDeclaredAnnotation(PluginSubGroup.class);
                        var subGroupName =  cls.getPackageName().substring(cls.getPackageName().lastIndexOf('.') + 1);
                        var subGroupTitle = pluginSubGroup != null ? pluginSubGroup.title() : subGroupName;
                        var subGroupDescription = pluginSubGroup != null ? pluginSubGroup.description() : null;
                        // hack to avoid adding the subgroup in the task URL when it's the group to keep search engine indexes
                        var subgroupIsGroup = cls.getPackageName().length() <= this.group.length();
                        var subgroup = new SubGroup(subGroupName, subGroupTitle, subGroupDescription, subgroupIsGroup);
                        builder.subgroup(subgroup);
                    } else {
                        // should never occur
                        builder.subgroup(new SubGroup(this.group.substring(this.group.lastIndexOf('.') + 1)));
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
        SubGroup subgroup;
        String group;
        String type;
    }

    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode(of = "name")
    public static class SubGroup implements Comparable<SubGroup>{
        String name;
        String title;
        String description;

        boolean subgroupIsGroup;

        SubGroup(String name) {
            this.name = name;
        }

        @Override
        public int compareTo(SubGroup o) {
            return name.compareTo(o.getName());
        }
    }
}
