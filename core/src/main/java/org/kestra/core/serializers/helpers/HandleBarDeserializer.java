package org.kestra.core.serializers.helpers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.HandlebarsException;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.PartialHelper;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.google.common.collect.ImmutableMap;
import org.kestra.core.models.validations.ManualConstraintViolation;
import org.kestra.core.serializers.YamlFlowParser;

import java.io.IOException;
import java.util.Collections;
import javax.validation.ConstraintViolationException;

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


    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();

        if (value.contains("[[") && value.contains("]]")) {
            String contextPath = String.valueOf(ctxt.findInjectableValue(
                YamlFlowParser.CONTEXT_FLOW_DIRECTORY,
                null,
                null
            ));

            TemplateLoader loader = new FileTemplateLoader(contextPath, "");
            Handlebars handlebars = HANDLEBARS.with(loader);
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
}
