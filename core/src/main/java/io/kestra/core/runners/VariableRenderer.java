package io.kestra.core.runners;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.HandlebarsException;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.*;
import io.kestra.core.utils.IdUtils;
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
    private static final Pattern RAW_PATTERN = Pattern.compile("\\{%[-]*\\s*raw\\s*[-]*%\\}(.*?)\\{%[-]*\\s*endraw\\s*[-]*%\\}");
    public static final int MAX_RENDERING_AMOUNT = 100;

    private Handlebars handlebars;
    private PebbleEngine pebbleEngine;
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

        this.pebbleEngine = pebbleBuilder.build();
    }

    public static IllegalVariableEvaluationException properPebbleException(PebbleException e) {
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
        if (inline == null) {
            return null;
        }

        if (inline.indexOf('{') == -1) {
            // it's not a Pebble template so we short-circuit rendering
            return inline;
        }

        String result;
        if (this.variableConfiguration.getRecursiveRendering()) {
            result = renderRecursively(inline, variables);
        } else {
            result = renderOnce(inline,  variables);
        }


        return result;
    }

    public String renderOnce(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        // pre-process raw tags
        Matcher rawMatcher = RAW_PATTERN.matcher(inline);
        Map<String, String> replacers = new HashMap<>((int) Math.ceil(rawMatcher.groupCount() / 0.75));
        String result = rawMatcher.replaceAll(matchResult -> {
            var uuid = UUID.randomUUID().toString();
            replacers.put(uuid, matchResult.group(1));
            return uuid;
        });

        try {
            PebbleTemplate compiledTemplate = this.pebbleEngine.getLiteralTemplate(result);

            Writer writer = new JsonWriter(new StringWriter());
            compiledTemplate.evaluate(writer, variables);
            result = writer.toString();
        } catch (IOException | PebbleException e) {
            if (this.handlebars == null) {
                if (e instanceof PebbleException) {
                    throw properPebbleException((PebbleException) e);
                }

                throw new IllegalVariableEvaluationException(e);
            }

            try {
                Template  template = this.handlebars.compileInline(inline);
                result = template.apply(variables);
            } catch (HandlebarsException | IOException hbE) {
                throw new IllegalVariableEvaluationException(
                    "Pebble evaluation failed with '" + e.getMessage() +  "' " +
                        "and Handlebars fallback failed also  with '" + hbE.getMessage() + "'" ,
                    e
                );
            }
        }

        // post-process raw tags
        for (var entry : replacers.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }

        return result;
    }

    public String renderRecursively(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return this.renderRecursively(0, inline, variables);
    }

    private String renderRecursively(int renderingCount, String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        if (renderingCount > MAX_RENDERING_AMOUNT) {
            throw new IllegalVariableEvaluationException("Too many rendering attempts");
        }

        String result = this.renderOnce(inline, variables);
        if (result.equals(inline)) {
            return result;
        }

        return renderRecursively(++renderingCount, result, variables);
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
            result.add(this.render(inline, variables));
        }

        return result;
    }

    public Set<String> render(Set<String> list, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        Set<String> result = new HashSet<>();
        for (String inline : list) {
            result.add(this.render(inline, variables));
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
            this.recursiveRendering = false;
        }

        Boolean disableHandlebars;
        Boolean cacheEnabled;
        Integer cacheSize;
        Boolean recursiveRendering;
    }
}
