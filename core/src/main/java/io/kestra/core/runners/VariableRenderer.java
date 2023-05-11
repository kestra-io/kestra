package io.kestra.core.runners;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.HandlebarsException;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.*;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.error.AttributeNotFoundException;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.handlebars.VariableRendererPlugins;
import io.kestra.core.runners.handlebars.helpers.*;
import io.kestra.core.runners.pebble.ExtensionCustomizer;
import io.kestra.core.runners.pebble.JsonWriter;
import io.kestra.core.runners.pebble.PebbleLruCache;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.ConfigurationProperties;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;

@Singleton
public class VariableRenderer {
    private Handlebars handlebars;
    private final PebbleEngine pebbleEngine;
    private final VariableConfiguration variableConfiguration;

    @SuppressWarnings("unchecked")
    @Inject
    public VariableRenderer(ApplicationContext applicationContext, @Nullable VariableConfiguration variableConfiguration) {
        this.variableConfiguration = variableConfiguration != null ? variableConfiguration : new VariableConfiguration();

        if (!this.variableConfiguration.getDisableHandlebars()) {
            this.handlebars = new Handlebars()
                .with(EscapingStrategy.NOOP)
                .registerHelpers(ConditionalHelpers.class)
                .registerHelpers(EachHelper.class)
                .registerHelpers(LogHelper.class)
                .registerHelpers(StringHelpers.class)
                .registerHelpers(OtherStringsHelper.class)
                .registerHelpers(UnlessHelper.class)
                .registerHelpers(WithHelper.class)
                .registerHelpers(DateHelper.class)
                .registerHelpers(JsonHelper.class)
                .registerHelpers(MiscHelper.class)
                .registerHelpers(OtherBooleansHelper.class)
                .registerHelper("eval", new EvalHelper(this))
                .registerHelper("firstDefinedEval", new FirstDefinedEvalHelper(this))
                .registerHelper("jq", new JqHelper())
                .registerHelperMissing((context, options) -> {
                    throw new IllegalStateException("Missing variable: " + options.helperName);
                });

            applicationContext.getBeansOfType(VariableRendererPlugins.class)
                .forEach(variableRendererPlugins -> {
                    this.handlebars.registerHelper(
                        variableRendererPlugins.name(),
                        variableRendererPlugins.helper()
                    );
                });
        }

        PebbleEngine.Builder pebbleBuilder = new PebbleEngine.Builder()
            .registerExtensionCustomizer(ExtensionCustomizer::new)
            .strictVariables(true)
            .cacheActive(this.variableConfiguration.getCacheEnabled())

            .newLineTrimming(false)
            .autoEscaping(false);

        applicationContext.getBeansOfType(AbstractExtension.class)
            .forEach(pebbleBuilder::extension);

        if (this.variableConfiguration.getCacheEnabled()) {
            pebbleBuilder.templateCache(new PebbleLruCache(this.variableConfiguration.getCacheSize()));
        }

        pebbleEngine = pebbleBuilder.build();
    }

    public String recursiveRender(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        if (inline == null) {
            return null;
        }

        boolean isSame = false;
        String currentTemplate = inline;
        String current = "";
        PebbleTemplate compiledTemplate;
        while (!isSame) {
            try {
                compiledTemplate = pebbleEngine.getLiteralTemplate(currentTemplate);

                Writer writer = new JsonWriter(new StringWriter());
                compiledTemplate.evaluate(writer, variables);
                current = writer.toString();
            } catch (IOException | PebbleException e) {
                if (this.variableConfiguration.disableHandlebars) {
                    if (e instanceof PebbleException) {
                        throw properPebbleException((PebbleException) e);
                    }

                    throw new IllegalVariableEvaluationException(e);
                }

                try {
                    Template  template = handlebars.compileInline(currentTemplate);
                    current = template.apply(variables);
                } catch (HandlebarsException | IOException hbE) {
                    throw new IllegalVariableEvaluationException(
                        "Pebble evaluation failed with '" + e.getMessage() +  "' " +
                        "and Handlebars fallback failed also  with '" + hbE.getMessage() + "'" ,
                        e
                    );
                }
            }

            isSame = currentTemplate.equals(current);
            currentTemplate = current;
        }

        return current;
    }

    public IllegalVariableEvaluationException properPebbleException(PebbleException e) {
        if (e instanceof AttributeNotFoundException) {
            AttributeNotFoundException current = (AttributeNotFoundException) e;

            return new IllegalVariableEvaluationException(
                "Missing variable: '" + current.getAttributeName() +
                    "' on '" + current.getFileName() +
                    "' at line " + current.getLineNumber(),
                e
            );
        }

        return new IllegalVariableEvaluationException(e);
    }

    public String render(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return this.recursiveRender(inline, variables);
    }


    public Map<String, Object> render(Map<String, Object> in, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        Map<String, Object> map = new HashMap<>();

        for (Map.Entry<String, Object> r : in.entrySet()) {
            String key = this.render(r.getKey(), variables);
            Object value = renderObject(r.getValue(), variables).orElse(r.getValue());

            map.putIfAbsent(
                key,
                value
            );
        }

        return map;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<Object> renderObject(Object object, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        if (object instanceof Map) {
            return Optional.of(this.render((Map) object, variables));
        } else if (object instanceof Collection) {
            return Optional.of(this.renderList((List) object, variables));
        } else if (object instanceof String) {
            return Optional.of(this.render((String) object, variables));
        }

        return Optional.empty();
    }

    public List<Object> renderList(List<Object> list, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        List<Object> result = new ArrayList<>();

        for (Object inline : list) {
            this.renderObject(inline, variables)
                .ifPresent(result::add);
        }

        return result;
    }

    public List<String> render(List<String> list, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        List<String> result = new ArrayList<>();
        for (String inline : list) {
            result.add(this.recursiveRender(inline, variables));
        }

        return result;
    }

    @Getter
    @ConfigurationProperties("kestra.variables")
    public static class VariableConfiguration {
        public VariableConfiguration() {
            this.disableHandlebars = true;
            this.cacheEnabled = true;
            this.cacheSize = 1000;
        }

        Boolean disableHandlebars;
        Boolean cacheEnabled;
        Integer cacheSize;
    }
}
