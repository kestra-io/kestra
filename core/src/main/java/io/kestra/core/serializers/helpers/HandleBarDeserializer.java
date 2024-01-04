package io.kestra.core.serializers.helpers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.HandlebarsException;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.PartialHelper;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.serializers.YamlFlowParser;
import lombok.AllArgsConstructor;

import java.io.IOException;
import java.util.Collections;
import javax.validation.ConstraintViolationException;

@AllArgsConstructor
public class HandleBarDeserializer extends StringDeserializer {
    private static final long serialVersionUID = 1L;

    private static final Handlebars HANDLEBARS = new Handlebars()
        .with(EscapingStrategy.NOOP)
        .startDelimiter("[[")
        .endDelimiter("]]")
        .registerHelpers(PartialHelper.class)
        .registerHelperMissing((context, options) -> {
            throw new IllegalStateException("Missing variable: " + options.helperName);
        });
    private static final ClassPathTemplateLoader DEFAULT_TEMPLATE_LOADER = new ClassPathTemplateLoader();

    private final boolean withFlowDirectoryContext;

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();

        if (value.contains("[[") && value.contains("]]")) {
            Handlebars handlebars = setupHandlebars(ctxt);

            Template template = handlebars.compileInline(value);

            try {
                return template.apply(ImmutableMap.of());
            } catch (IOException | HandlebarsException e) {
                throw new ConstraintViolationException(Collections.singleton(ManualConstraintViolation.of(
                    e.getMessage(),
                    this,
                    HandleBarDeserializer.class,
                    "template",
                    value
                )));
            }
        }

        return super.deserialize(p, ctxt);
    }

    private Handlebars setupHandlebars(DeserializationContext ctxt) throws JsonMappingException {
        Handlebars handlebars = HANDLEBARS;

        if (withFlowDirectoryContext) {
            String contextPath = String.valueOf(ctxt.findInjectableValue(
                YamlFlowParser.CONTEXT_FLOW_DIRECTORY,
                null,
                null
            ));
            TemplateLoader loader = new FileTemplateLoader(contextPath, "");
            return handlebars.with(loader);
        }

        return handlebars.with(DEFAULT_TEMPLATE_LOADER);
    }
}
