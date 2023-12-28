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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;

@Singleton
public class VariableRenderer {
    private static final Pattern RAW_OR_MAX_RENDER_PATTERN = Pattern.compile("\\{%-*\\s*(raw|maxRender)\\s*\\d*\\s*-*%}\\s*([\\s\\S]*?)\\s*\\{%-*\\s*end(?:raw|maxRender)\\s*-*%}");

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

    public String recursiveRender(String initialTemplateToRender, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        if (initialTemplateToRender == null) {
            return null;
        }

        if (initialTemplateToRender.indexOf('{') == -1) {
            // it's not a Pebble template so we short-circuit rendering
            return initialTemplateToRender;
        }

        Map<String, String> replacers = null;
        boolean isSame = false;
        boolean remainingTagsToPreprocess = true;
        String rendered = initialTemplateToRender;
        PebbleTemplate compiledTemplate;
        while (!isSame) {
            String beforeRender = rendered;

            // pre-process raw tags
            if (remainingTagsToPreprocess) {
                Matcher preprocessTagMatcher = RAW_OR_MAX_RENDER_PATTERN.matcher(beforeRender);
                if (preprocessTagMatcher.find()) {
                    if(replacers == null) {
                        replacers = new HashMap<>((int) Math.ceil(preprocessTagMatcher.results().count() / 0.75));
                    }

                    Map<String, String> finalReplacers = replacers;
                    beforeRender = preprocessTagMatcher.replaceAll(result -> {
                        if (result.group(1).equals("raw")) {
                            String replacerKey = UUID.randomUUID().toString();
                            finalReplacers.put(replacerKey, result.group(2));
                            return replacerKey;
                        }

                        // don't replace maxRender tags
                        return result.group(0);
                    });
                } else {
                    remainingTagsToPreprocess = false;
                }
            }

            try {
                compiledTemplate = pebbleEngine.getLiteralTemplate(beforeRender);

                Writer writer = new JsonWriter(new StringWriter());
                compiledTemplate.evaluate(writer, variables);
                rendered = writer.toString();
            } catch (IOException | PebbleException e) {
                if (this.variableConfiguration.disableHandlebars) {
                    if (e instanceof PebbleException) {
                        throw properPebbleException((PebbleException) e);
                    }

                    throw new IllegalVariableEvaluationException(e);
                }

                try {
                    Template  template = handlebars.compileInline(beforeRender);
                    rendered = template.apply(variables);
                } catch (HandlebarsException | IOException hbE) {
                    throw new IllegalVariableEvaluationException(
                        "Pebble evaluation failed with '" + e.getMessage() +  "' " +
                        "and Handlebars fallback failed also  with '" + hbE.getMessage() + "'" ,
                        e
                    );
                }
            }

            isSame = beforeRender.equals(rendered);
        }

        if (replacers != null) {
            // post-process raw tags
            for (var entry : replacers.entrySet()) {
                rendered = rendered.replace(entry.getKey(), entry.getValue());
            }
        }
        return rendered;
    }

    public IllegalVariableEvaluationException properPebbleException(PebbleException e) {
        if (e instanceof AttributeNotFoundException current) {
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

    public Set<String> render(Set<String> list, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        Set<String> result = new HashSet<>();
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
