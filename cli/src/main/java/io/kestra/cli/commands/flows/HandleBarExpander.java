package io.kestra.cli.commands.flows;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.PartialHelper;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.serializers.helpers.FileTemplateLoader;

import java.io.IOException;
import java.nio.file.Path;

public abstract class HandleBarExpander {
    private static final Handlebars HANDLEBARS = new Handlebars()
        .with(EscapingStrategy.NOOP)
        .startDelimiter("[[")
        .endDelimiter("]]")
        .registerHelpers(PartialHelper.class)
        .registerHelperMissing((context, options) -> {
            throw new IllegalStateException("Missing variable: " + options.helperName);
        });


    public static String expand(String value, Path directory) throws IOException {
        if (value.contains("[[") && value.contains("]]")) {
            String contextPath = directory.toString();

            TemplateLoader loader = new FileTemplateLoader(contextPath, "");
            Handlebars handlebars = HANDLEBARS.with(loader);
            Template template = handlebars.compileInline(value);

            return template.apply(ImmutableMap.of());
        }

        return value;
    }
}
