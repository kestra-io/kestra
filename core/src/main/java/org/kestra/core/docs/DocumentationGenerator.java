package org.kestra.core.docs;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.*;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.IOUtils;
import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.plugins.RegisteredPlugin;
import org.kestra.core.runners.handlebars.helpers.DateHelper;
import org.kestra.core.runners.handlebars.helpers.JsonHelper;
import org.kestra.core.serializers.JacksonMapper;
import org.kestra.core.utils.Slugify;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

abstract public class DocumentationGenerator {
    private static final Handlebars handlebars = new Handlebars()
        .with(EscapingStrategy.NOOP)
        .registerHelpers(ConditionalHelpers.class)
        .registerHelpers(EachHelper.class)
        .registerHelpers(LogHelper.class)
        .registerHelpers(StringHelpers.class)
        .registerHelpers(UnlessHelper.class)
        .registerHelpers(WithHelper.class)
        .registerHelpers(DateHelper.class)
        .registerHelpers(JsonHelper.class);

    public static List<Document> generate(RegisteredPlugin registeredPlugin) throws IOException {
        ArrayList<Document> result = new ArrayList<>();

        result.addAll(DocumentationGenerator.index(registeredPlugin));

        result.addAll(DocumentationGenerator.generate(registeredPlugin, registeredPlugin.getTasks(), Task.class, "tasks"));
        result.addAll(DocumentationGenerator.generate(registeredPlugin, registeredPlugin.getTriggers(), AbstractTrigger.class, "triggers"));
        result.addAll(DocumentationGenerator.generate(registeredPlugin, registeredPlugin.getConditions(), Condition.class, "conditions"));

        return result;
    }

    private static List<Document> index(RegisteredPlugin plugin) throws IOException {
        PluginDocumentation pluginDocumentation = PluginDocumentation.of(plugin);

        if (pluginDocumentation.getClassPlugins().size() == 0) {
            return Collections.emptyList();
        }

        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();

        if (plugin.getManifest() != null) {
            builder.put("title", plugin.getManifest().getMainAttributes().getValue("X-Kestra-Title"));
            builder.put("group", plugin.getManifest().getMainAttributes().getValue("X-Kestra-Group"));
            builder.put("docs", JacksonMapper.toMap(pluginDocumentation));
        }

        return Collections.singletonList(new Document(
            docPath(plugin),
            render("index", builder.build())
        ));
    }

    private static <T> List<Document> generate(RegisteredPlugin registeredPlugin, List<Class<? extends T>> cls, Class<T> baseCls, String type) {
        return cls
            .stream()
            .map(r -> ClassPluginDocumentation.of(registeredPlugin, r, baseCls))
            .map(pluginDocumentation -> {
                try {
                    return new Document(
                        docPath(registeredPlugin, type, pluginDocumentation),
                        render(pluginDocumentation)
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toList());
    }

    public static String icon(RegisteredPlugin plugin, Class<?> cls ) {
        try (InputStream resourceAsStream = plugin.getClassLoader().getResourceAsStream("icons/" + cls.getName() + ".svg")) {
            if (resourceAsStream != null) {
                return Base64.getEncoder().encodeToString(
                    IOUtils.toString(resourceAsStream, Charsets.UTF_8).getBytes(StandardCharsets.UTF_8)
                );
            }
        } catch (IOException ignored) {

        }

        return null;
    }

    private static <T> String docPath(RegisteredPlugin registeredPlugin) {
        String pluginName = Slugify.of(registeredPlugin.title());

        return pluginName + "/README.md";
    }

    private static <T> String docPath(RegisteredPlugin registeredPlugin, String type, ClassPluginDocumentation<T> classPluginDocumentation) {
        String pluginName = Slugify.of(registeredPlugin.title());

        return pluginName + "/" + type + "/" +
            (classPluginDocumentation.getSubGroup() != null ? classPluginDocumentation.getSubGroup() + "/" : "") +
            classPluginDocumentation.getCls() + ".md";
    }

    public static <T> String render(ClassPluginDocumentation<T> classPluginDocumentation) throws IOException {
        return render("task", JacksonMapper.toMap(classPluginDocumentation));
    }

    public static <T> String render(String templateName, Map<String, Object> vars) throws IOException {
        String hbsTemplate = IOUtils.toString(
            Objects.requireNonNull(DocumentationGenerator.class.getClassLoader().getResourceAsStream("docs/" + templateName + ".hbs")),
            Charsets.UTF_8
        );

        Template template = handlebars.compileInline(hbsTemplate);
        String renderer = template.apply(vars);

        // vuepress {{ }} evaluation
        Pattern pattern = Pattern.compile("`\\{\\{(.*?)\\}\\}`", Pattern.MULTILINE);
        renderer = pattern.matcher(renderer).replaceAll("<code v-pre>{{ $1 }}</code>");

        return renderer;
    }
}
