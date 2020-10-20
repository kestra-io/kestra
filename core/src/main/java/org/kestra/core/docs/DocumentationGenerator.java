package org.kestra.core.docs;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.*;
import com.github.jknack.handlebars.internal.lang3.ObjectUtils;
import com.google.common.base.Charsets;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.plugins.RegisteredPlugin;
import org.kestra.core.runners.handlebars.helpers.DateHelper;
import org.kestra.core.runners.handlebars.helpers.JsonHelper;
import org.kestra.core.serializers.JacksonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public static List<Document> generate(RegisteredPlugin registeredPlugin) {
        ArrayList<Document> result = new ArrayList<>();

        result.addAll(DocumentationGenerator.generate(registeredPlugin, registeredPlugin.getTasks(), Task.class, "tasks"));
        result.addAll(DocumentationGenerator.generate(registeredPlugin, registeredPlugin.getTriggers(), AbstractTrigger.class, "triggers"));
        result.addAll(DocumentationGenerator.generate(registeredPlugin, registeredPlugin.getConditions(), Condition.class, "conditions"));

        return result;
    }

    private static <T> List<Document> generate(RegisteredPlugin registeredPlugin, List<Class<? extends T>> cls, Class<T> baseCls, String type) {
        return cls
            .stream()
            .map(r -> PluginDocumentation.of(registeredPlugin, r, baseCls))
            .map(pluginDocumentation -> {
                try {
                    String project = ObjectUtils.firstNonNull(
                        registeredPlugin.getManifest() != null ? registeredPlugin.getManifest().getMainAttributes().getValue("X-Kestra-Title") : null,
                        registeredPlugin.getExternalPlugin() != null ? FilenameUtils.getBaseName(registeredPlugin.getExternalPlugin().getLocation().getPath()) : null,
                        "core"
                    );

                    return new Document(
                        project + "/" + type + "/" +
                            (pluginDocumentation.getSubGroup() != null ? pluginDocumentation.getSubGroup() + "/" : "") +
                            pluginDocumentation.getCls() + ".md",
                        render(pluginDocumentation)
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toList());
    }

    public static <T> String render(PluginDocumentation<T> pluginDocumentation) throws IOException {
        String hbsTemplate = IOUtils.toString(
            Objects.requireNonNull(DocumentationGenerator.class.getClassLoader().getResourceAsStream("docs/task.hbs")),
            Charsets.UTF_8
        );

        Template template = handlebars.compileInline(hbsTemplate);
        Map<String, Object> vars = JacksonMapper.toMap(pluginDocumentation);
        String renderer = template.apply(vars);

        // vuepress {{ }} evaluation
        Pattern pattern = Pattern.compile("`\\{\\{(.*?)\\}\\}`", Pattern.MULTILINE);
        renderer = pattern.matcher(renderer).replaceAll("<code v-pre>{{ $1 }}</code>");

        return renderer;
    }
}
