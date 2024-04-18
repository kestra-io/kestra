package io.kestra.core.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.pebble.ExtensionCustomizer;
import io.kestra.core.runners.pebble.JsonWriter;
import io.kestra.core.runners.pebble.PebbleLruCache;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.error.AttributeNotFoundException;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class VariableRenderer {
    private static final Pattern RAW_PATTERN = Pattern.compile("(\\{%-*\\s*raw\\s*-*%}(.*?)\\{%-*\\s*endraw\\s*-*%})");
    public static final int MAX_RENDERING_AMOUNT = 100;

    private final PebbleEngine pebbleEngine;
    private final VariableConfiguration variableConfiguration;

    @Inject
    public VariableRenderer(ApplicationContext applicationContext, @Nullable VariableConfiguration variableConfiguration) {
        this.variableConfiguration = variableConfiguration != null ? variableConfiguration : new VariableConfiguration();

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
        return this.render(inline, variables, this.variableConfiguration.getRecursiveRendering());
    }

    public String render(String inline, Map<String, Object> variables, boolean recursive) throws IllegalVariableEvaluationException {
        if (inline == null) {
            return null;
        }

        if (inline.indexOf('{') == -1) {
            // it's not a Pebble template so we short-circuit rendering
            return inline;
        }

        String render = recursive
            ? renderRecursively(inline, variables)
            : renderOnce(inline, variables);

        return RAW_PATTERN.matcher(render).replaceAll("$2");
    }

    public String renderOnce(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        // pre-process raw tags
        Matcher rawMatcher = RAW_PATTERN.matcher(inline);
        Map<String, String> replacers = new HashMap<>((int) Math.ceil(rawMatcher.groupCount() / 0.75));
        String result = replaceRawTags(rawMatcher, replacers);

        try {
            PebbleTemplate compiledTemplate = this.pebbleEngine.getLiteralTemplate(result);

            Writer writer = new JsonWriter(new StringWriter());
            compiledTemplate.evaluate(writer, variables);
            result = writer.toString();
        } catch (IOException | PebbleException e) {
            String alternativeRender = this.alternativeRender(e, inline, variables);
            if (alternativeRender == null) {
                if (e instanceof PebbleException) {
                    throw properPebbleException((PebbleException) e);
                }
                throw new IllegalVariableEvaluationException(e);
            } else {
                result = alternativeRender;
            }
        }

        // post-process raw tags
        result = putBackRawTags(replacers, result);

        return result;
    }

    /**
     * This method can be used in fallback for rendering an input string.
     *
     * @param e         The exception that was throw by the default variable renderer.
     * @param inline    The expression to be rendered.
     * @param variables The context variables.
     * @return          The rendered string.
     */
    protected String alternativeRender(Exception e, String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return null;
    }

    private static String putBackRawTags(Map<String, String> replacers, String result) {
        for (var entry : replacers.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static String replaceRawTags(Matcher rawMatcher, Map<String, String> replacers) {
        return rawMatcher.replaceAll(matchResult -> {
            var uuid = UUID.randomUUID().toString();
            replacers.put(uuid, matchResult.group(1));
            return uuid;
        });
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
        return this.render(in, variables, this.variableConfiguration.getRecursiveRendering());
    }

    public Map<String, Object> render(Map<String, Object> in, Map<String, Object> variables, boolean recursive) throws IllegalVariableEvaluationException {
        Map<String, Object> map = new HashMap<>();

        for (Map.Entry<String, Object> r : in.entrySet()) {
            String key = this.render(r.getKey(), variables);
            Object value = renderObject(r.getValue(), variables, recursive).orElse(r.getValue());

            map.putIfAbsent(
                key,
                value
            );
        }

        return map;
    }

    public Optional<Object> renderObject(Object object, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return this.renderObject(object, variables, this.variableConfiguration.getRecursiveRendering());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<Object> renderObject(Object object, Map<String, Object> variables, boolean recursive) throws IllegalVariableEvaluationException {
        if (object instanceof Map map) {
            return Optional.of(this.render(map, variables, recursive));
        } else if (object instanceof List list) {
            return Optional.of(this.renderList(list, variables, recursive));
        } else if (object instanceof Set set) {
            return Optional.of(this.render(set, variables, recursive));
        } else if (object instanceof String string) {
            return Optional.of(this.render(string, variables, recursive));
        }

        // Return the given object if it cannot be rendered.
        return Optional.ofNullable(object);
    }

    public List<Object> renderList(List<Object> list, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return this.renderList(list, variables, this.variableConfiguration.getRecursiveRendering());
    }

    public List<Object> renderList(List<Object> list, Map<String, Object> variables, boolean recursive) throws IllegalVariableEvaluationException {
        List<Object> result = new ArrayList<>();

        for (Object inline : list) {
            result.add(this.renderObject(inline, variables, recursive).orElse(inline));
        }

        return result;
    }

    public List<String> render(List<String> list, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return this.render(list, variables, this.variableConfiguration.getRecursiveRendering());
    }

    public List<String> render(List<String> list, Map<String, Object> variables, boolean recursive) throws IllegalVariableEvaluationException {
        List<String> result = new ArrayList<>();
        for (String inline : list) {
            result.add(this.render(inline, variables, recursive));
        }

        return result;
    }

    public Set<String> render(Set<String> set, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return this.render(set, variables, this.variableConfiguration.getRecursiveRendering());
    }

    public Set<String> render(Set<String> list, Map<String, Object> variables, boolean recursive) throws IllegalVariableEvaluationException {
        Set<String> result = new HashSet<>();
        for (String inline : list) {
            result.add(this.render(inline, variables, recursive));
        }

        return result;
    }

    @Getter
    @ConfigurationProperties("kestra.variables")
    public static class VariableConfiguration {
        public VariableConfiguration() {
            this.cacheEnabled = true;
            this.cacheSize = 1000;
            this.recursiveRendering = false;
        }

        Boolean cacheEnabled;
        Integer cacheSize;
        Boolean recursiveRendering;
    }
}
