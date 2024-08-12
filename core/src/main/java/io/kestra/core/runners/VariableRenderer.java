package io.kestra.core.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.pebble.*;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.error.AttributeNotFoundException;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableRenderer {
    private static final Pattern RAW_PATTERN = Pattern.compile("(\\{%-*\\s*raw\\s*-*%}(.*?)\\{%-*\\s*endraw\\s*-*%})");
    public static final int MAX_RENDERING_AMOUNT = 100;

    private final PebbleEngine pebbleEngine;
    private final boolean recursiveRendering;

    public VariableRenderer(final PebbleEngine pebbleEngine, final boolean recursiveRendering) {
        this.pebbleEngine = Objects.requireNonNull(pebbleEngine, "pebbleEngine cannot be null");
        this.recursiveRendering = recursiveRendering;
    }

    // For backward-compatibility only
    public VariableRenderer(ApplicationContext applicationContext, @Nullable VariableConfiguration variableConfiguration) {
        this(
            applicationContext.getBean(PebbleEngine.class),
            Optional.ofNullable(variableConfiguration).map(VariableConfiguration::recursiveRendering).orElse(false)
        );
    }

    public static IllegalVariableEvaluationException properPebbleException(PebbleException e) {
        if (e instanceof AttributeNotFoundException current) {
            return new IllegalVariableEvaluationException(
                "Unable to find `" + current.getAttributeName() +
                    "` used in the expression `" + current.getFileName() +
                    "` at line " + current.getLineNumber(),
                e
            );
        }

        return new IllegalVariableEvaluationException(e);
    }

    public String render(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return this.render(inline, variables, recursiveRendering);
    }

    public Object renderTyped(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return this.render(inline, variables, recursiveRendering, false);
    }

    public String render(String inline, Map<String, Object> variables, boolean recursive) throws IllegalVariableEvaluationException {
        return (String) this.render(inline, variables, recursive, true);
    }

    public Object render(Object inline, Map<String, Object> variables, boolean recursive, boolean stringify) throws IllegalVariableEvaluationException {
        if (inline == null) {
            return null;
        }

        if (inline instanceof String inlineStr && inlineStr.indexOf('{') == -1) {
            // it's not a Pebble template so we short-circuit rendering
            return inline;
        }

        Object render = recursive
            ? renderRecursively(inline, variables, stringify)
            : renderOnce(inline, variables, stringify);

        if (render instanceof String renderStr) {
            return RAW_PATTERN.matcher(renderStr).replaceAll("$2");
        }

        return render;
    }

    public Object renderOnce(Object inline, Map<String, Object> variables, boolean stringify) throws IllegalVariableEvaluationException {
        Object result = inline;
        Map<String, String> replacers = null;
        if (inline instanceof String inlineStr) {
            // pre-process raw tags
            Matcher rawMatcher = RAW_PATTERN.matcher(inlineStr);
            replacers = new HashMap<>((int) Math.ceil(rawMatcher.groupCount() / 0.75));
            result = replaceRawTags(rawMatcher, replacers);
        }

        try {
            PebbleTemplate compiledTemplate = this.pebbleEngine.getLiteralTemplate((String) result);

            OutputWriter writer = stringify ? new JsonWriter() : new TypedObjectWriter();
            compiledTemplate.evaluate(writer, variables);
            result = writer.output();
        } catch (IOException | PebbleException e) {
            String alternativeRender = this.alternativeRender(e, (String) inline, variables);
            if (alternativeRender == null) {
                if (e instanceof PebbleException) {
                    throw properPebbleException((PebbleException) e);
                }
                throw new IllegalVariableEvaluationException(e);
            } else {
                result = alternativeRender;
            }
        }

        if (result instanceof String && replacers != null) {
            // post-process raw tags
            result = putBackRawTags(replacers, (String) result);
        }

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

    public Object renderRecursively(Object inline, Map<String, Object> variables, boolean stringify) throws IllegalVariableEvaluationException {
        return this.renderRecursively(0, inline, variables, stringify);
    }

    private Object renderRecursively(int renderingCount, Object inline, Map<String, Object> variables, boolean stringify) throws IllegalVariableEvaluationException {
        if (renderingCount > MAX_RENDERING_AMOUNT) {
            throw new IllegalVariableEvaluationException("Too many rendering attempts");
        }

        Object result = this.renderOnce(inline, variables, stringify);
        if (result.equals(inline)) {
            return result;
        }

        return renderRecursively(++renderingCount, result, variables, stringify);
    }

    public Map<String, Object> render(Map<String, Object> in, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return this.render(in, variables, recursiveRendering);
    }

    public Map<String, Object> render(Map<String, Object> in, Map<String, Object> variables, boolean recursive) throws IllegalVariableEvaluationException {
        Map<String, Object> map = new LinkedHashMap<>();

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
        return this.renderObject(object, variables, recursiveRendering);
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
        return this.renderList(list, variables, recursiveRendering);
    }

    public List<Object> renderList(List<Object> list, Map<String, Object> variables, boolean recursive) throws IllegalVariableEvaluationException {
        List<Object> result = new ArrayList<>();

        for (Object inline : list) {
            result.add(this.renderObject(inline, variables, recursive).orElse(inline));
        }

        return result;
    }

    public List<String> render(List<String> list, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return this.render(list, variables, recursiveRendering);
    }

    public List<String> render(List<String> list, Map<String, Object> variables, boolean recursive) throws IllegalVariableEvaluationException {
        List<String> result = new ArrayList<>();
        for (String inline : list) {
            result.add(this.render(inline, variables, recursive));
        }

        return result;
    }

    public Set<String> render(Set<String> set, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return this.render(set, variables, recursiveRendering);
    }

    public Set<String> render(Set<String> list, Map<String, Object> variables, boolean recursive) throws IllegalVariableEvaluationException {
        Set<String> result = new HashSet<>();
        for (String inline : list) {
            result.add(this.render(inline, variables, recursive));
        }

        return result;
    }

    @ConfigurationProperties("kestra.variables")
    public static record VariableConfiguration(
        @Bindable(defaultValue = "true")
        Boolean cacheEnabled,
        @Bindable(defaultValue = "1000")
        Integer cacheSize,
        @Bindable(defaultValue = "false")
        Boolean recursiveRendering
    ) {
    }
}
