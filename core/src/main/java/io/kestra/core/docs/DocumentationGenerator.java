package io.kestra.core.docs;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.annotations.PluginSubGroup;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.script.ScriptRunner;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.runners.pebble.Extension;
import io.kestra.core.runners.pebble.JsonWriter;
import io.kestra.core.runners.pebble.filters.*;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Slugify;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Singleton
public class DocumentationGenerator {
    private static PebbleEngine pebbleEngine;

    @Inject
    JsonSchemaGenerator jsonSchemaGenerator;

    static {
        ClasspathLoader classpathLoader = new ClasspathLoader();
        classpathLoader.setPrefix("docs/");

        pebbleEngine = new PebbleEngine.Builder()
            .newLineTrimming(false)
            .loader(classpathLoader)
            .extension(new AbstractExtension() {
                @Override
                public Map<String, Filter> getFilters() {
                    Map<String, Filter> filters = new HashMap<>();
                    filters.put("json", new JsonFilter());
                    return filters;
                }
            })
            .autoEscaping(false)
            .extension(new Extension())
            .build();
    }

    public List<Document> generate(RegisteredPlugin registeredPlugin) throws Exception {
        ArrayList<Document> result = new ArrayList<>();

        result.addAll(index(registeredPlugin));

        result.addAll(this.generate(registeredPlugin, registeredPlugin.getTasks(), Task.class, "tasks"));
        result.addAll(this.generate(registeredPlugin, registeredPlugin.getTriggers(), AbstractTrigger.class, "triggers"));
        result.addAll(this.generate(registeredPlugin, registeredPlugin.getConditions(), Condition.class, "conditions"));
        result.addAll(this.generate(registeredPlugin, registeredPlugin.getScriptRunners(), ScriptRunner.class, "script-runners"));

        result.addAll(guides(registeredPlugin));

        return result;
    }

    private static List<Document> index(RegisteredPlugin plugin) throws IOException {
        Map<SubGroup, Map<String, List<ClassPlugin>>> groupedClass = DocumentationGenerator.indexGroupedClass(plugin);


        if (groupedClass.isEmpty()) {
            return Collections.emptyList();
        }

        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();

        builder.put("title", plugin.title().replace("plugin-", ""));

        if (plugin.description() != null) {
            builder.put("description", plugin.description());
        }

        if (plugin.longDescription() != null) {
            builder.put("longDescription", plugin.longDescription());
        }

        builder.put("group", plugin.group());
        builder.put("classPlugins", groupedClass);

        if (plugin.icon("plugin-icon") != null) {
            builder.put("icon", plugin.icon("plugin-icon"));
        }

        if(!plugin.getGuides().isEmpty()) {
            builder.put("guides", plugin.getGuides());
        }

        return Collections.singletonList(new Document(
            docPath(plugin),
            render("index", builder.build()),
            plugin.icon("plugin-icon")
        ));
    }

    private static Map<SubGroup, Map<String, List<ClassPlugin>>> indexGroupedClass(RegisteredPlugin plugin) {
        return plugin.allClassGrouped()
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
                    if (cls.getPackageName().startsWith(plugin.group())) {
                        var pluginSubGroup = cls.getPackage().getDeclaredAnnotation(PluginSubGroup.class);
                        var subGroupName =  cls.getPackageName().length() > plugin.group().length() ?
                            cls.getPackageName().substring(plugin.group().length() + 1) : "";
                        var subGroupTitle = pluginSubGroup != null ? pluginSubGroup.title() : subGroupName;
                        var subGroupDescription = pluginSubGroup != null ? pluginSubGroup.description() : null;
                        // hack to avoid adding the subgroup in the task URL when it's the group to keep search engine indexes
                        var subgroupIsGroup = cls.getPackageName().length() <= plugin.group().length();
                        var subGroupIcon = plugin.icon(cls.getPackageName());
                        var subgroup = new SubGroup(subGroupName, subGroupTitle, subGroupDescription, subGroupIcon, subgroupIsGroup);
                        builder.subgroup(subgroup);
                    } else {
                        // should never occur
                        builder.subgroup(new SubGroup(""));
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
                Collectors.groupingBy(classPlugin -> Slugify.toStartCase(classPlugin.getType()))
            ));
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
        String icon;

        boolean subgroupIsGroup;

        SubGroup(String name) {
            this.name = name;
        }

        @Override
        public int compareTo(SubGroup o) {
            return name.compareTo(o.getName());
        }
    }

    private static List<Document> guides(RegisteredPlugin plugin) throws Exception {
        String pluginName = Slugify.of(plugin.title());

        return plugin
            .guides()
            .entrySet()
            .stream()
            .map(throwFunction(e -> new Document(
                pluginName + "/guides/" + e.getKey()  + ".md",
                e.getValue(),
                null
            )))
            .collect(Collectors.toList());
    }


    private <T> List<Document> generate(RegisteredPlugin registeredPlugin, List<Class<? extends T>> cls, Class<T> baseCls, String type) {
        return cls
            .stream()
            .map(r -> ClassPluginDocumentation.of(jsonSchemaGenerator, registeredPlugin, r, baseCls))
            .map(pluginDocumentation -> {
                try {
                    return new Document(
                        docPath(registeredPlugin, type, pluginDocumentation),
                        render(pluginDocumentation),
                        pluginDocumentation.getIcon()
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toList());
    }

    private static String docPath(RegisteredPlugin registeredPlugin) {
        String pluginName = Slugify.of(registeredPlugin.path());

        return pluginName + "/index.md";
    }

    private static <T> String docPath(RegisteredPlugin registeredPlugin, String type, ClassPluginDocumentation<T> classPluginDocumentation) {
        String pluginName = Slugify.of(registeredPlugin.path());

        return pluginName + "/" + type + "/" +
            (classPluginDocumentation.getSubGroup() != null ? classPluginDocumentation.getSubGroup() + "/" : "") +
            classPluginDocumentation.getCls() + ".md";
    }

    public static <T> String render(ClassPluginDocumentation<T> classPluginDocumentation) throws IOException {
        return render("task", JacksonMapper.toMap(classPluginDocumentation));
    }

    public static <T> String render(AbstractClassDocumentation<T> classInputDocumentation) throws IOException {
        return render("task", JacksonMapper.toMap(classInputDocumentation));
    }

    public static <T> String render(String templateName, Map<String, Object> vars) throws IOException {
        String pebbleTemplate = IOUtils.toString(
            Objects.requireNonNull(DocumentationGenerator.class.getClassLoader().getResourceAsStream("docs/" + templateName + ".peb")),
            Charsets.UTF_8
        );

        PebbleTemplate compiledTemplate = pebbleEngine.getLiteralTemplate(pebbleTemplate);

        Writer writer = new JsonWriter(new StringWriter());
        compiledTemplate.evaluate(writer, vars);
        String renderer = writer.toString();

        // vuepress {{ }} evaluation
        Pattern pattern = Pattern.compile("`\\{\\{(.*?)\\}\\}`", Pattern.MULTILINE);
        renderer = pattern.matcher(renderer).replaceAll("<code v-pre>{{ $1 }}</code>");

        return renderer;
    }
}
