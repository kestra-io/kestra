package io.kestra.core.runners;

import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.runners.pebble.PebbleEngineFactory;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.error.PebbleException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Renders Pebble expression strings for display purposes only — not for task execution.
 *
 * <p>
 * Resolution rules (see architecture decision in issue #16874):
 * <ul>
 * <li>{@code secret()} → {@code [secret: KEY]} (never invoked)</li>
 * <li>{@code env()} / {@code envs.*} → kept raw</li>
 * <li>Non-deterministic / IO / external functions ({@code now()}, {@code uuid()}, {@code kv()}, {@code read()}, …) → kept raw</li>
 * <li>{@code vars.*}, {@code flow.*}, {@code globals.*} → resolved</li>
 * <li>{@code inputs.*}, {@code outputs.*}, {@code execution.*} → resolved when an execution is present; raw otherwise</li>
 * </ul>
 *
 * <p>
 * Resolution is all-or-nothing: Pebble renders the whole template in a single pass, so a template
 * that contains any unresolvable expression (a function removed from the display engine, or a missing
 * variable under {@code strictVariables}) is kept entirely raw.
 */
@Singleton
@Slf4j
public class DisplayExpressionRenderer {

    private final PebbleEngine displayEngine;

    @Inject
    public DisplayExpressionRenderer(PebbleEngineFactory pebbleEngineFactory) {
        this.displayEngine = pebbleEngineFactory.createRestricted();
    }

    /**
     * Renders each expression for display and returns a map keyed by the raw expression,
     * so the caller can tell which rendered value belongs to which input.
     *
     * @param expressions the raw expressions to render (duplicates and nulls are ignored)
     * @param variables the variable context (flow-level, or execution-level)
     * @return a map {@code rawExpression -> resolvedValue}, insertion-ordered
     */
    public Map<String, String> render(List<String> expressions, Map<String, Object> variables) {
        var rendered = new LinkedHashMap<String, String>();
        if (expressions == null) {
            return rendered;
        }
        for (String expression : expressions) {
            if (expression != null) {
                rendered.put(expression, render(expression, variables));
            }
        }
        return rendered;
    }

    /**
     * Resolves a single template string for display.
     *
     * <p>
     * Rendering is all-or-nothing: if any expression in the template cannot be resolved, Pebble
     * aborts the render and the original template is returned unchanged.
     *
     * @param template the raw Pebble template (may be null or contain no expressions)
     * @param variables the variable context (flow-level, or execution-level)
     * @return the resolved display string, or the original template if it cannot be fully resolved
     */
    public String render(String template, Map<String, Object> variables) {
        if (template == null || !template.contains("{")) {
            return template;
        }

        try {
            var compiled = displayEngine.getLiteralTemplate(template);
            var writer = new StringWriter();
            compiled.evaluate(writer, variables);
            return writer.toString();
        } catch (PebbleException | IOException e) {
            // Missing variable, bad syntax, or a non-allowlisted function (removed from the display
            // engine, so Pebble reports it as unknown) — keep the template raw.
            return template;
        }
    }
}
