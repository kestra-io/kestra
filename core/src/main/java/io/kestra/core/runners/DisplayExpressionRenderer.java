package io.kestra.core.runners;

import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.kestra.core.runners.pebble.DisplayUnrenderableException;
import io.kestra.core.runners.pebble.PebbleEngineFactory;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.error.PebbleException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Renders Pebble expression strings for display purposes only — not for task execution.
 *
 * <p>Resolution rules (see architecture decision in issue #16874):
 * <ul>
 *   <li>{@code secret()} → {@code [secret: KEY]} (never invoked)</li>
 *   <li>{@code env()} → {@code [env: NAME]} (never invoked)</li>
 *   <li>Non-deterministic / IO functions ({@code now()}, {@code uuid()}, {@code kv()}, {@code read()}, …) → kept raw</li>
 *   <li>{@code vars.*}, {@code flow.*}, {@code globals.*} → resolved</li>
 *   <li>{@code inputs.*}, {@code outputs.*}, {@code execution.*} → resolved when an execution is present; raw otherwise</li>
 * </ul>
 *
 * <p>Segment-level resolution: mixed strings like {@code "{{ vars.region }}-{{ now() }}"}
 * produce {@code "us-east-1-{{ now() }}"} — each {@code {{ }}} block is rendered independently and
 * unresolvable blocks keep their raw text.
 */
@Singleton
@Slf4j
public class DisplayExpressionRenderer {

    // Matches a single {{ ... }} expression block.
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\{\\{(.*?)}}");

    private final PebbleEngine displayEngine;

    @Inject
    public DisplayExpressionRenderer(PebbleEngineFactory pebbleEngineFactory) {
        this.displayEngine = pebbleEngineFactory.createForDisplay();
    }

    /**
     * Renders each expression for display and returns a map keyed by the raw expression,
     * so the caller can tell which rendered value belongs to which input.
     *
     * @param expressions the raw expressions to render (duplicates and nulls are ignored)
     * @param variables   the variable context (flow-level, or execution-level)
     * @return a map {@code rawExpression -> resolvedValue}, insertion-ordered
     */
    public Map<String, String> render(List<String> expressions, Map<String, Object> variables) {
        var rendered = new LinkedHashMap<String, String>();
        if (expressions == null) {
            return rendered;
        }
        for (String expression : expressions) {
            if (expression != null) {
                rendered.put(expression, resolveForDisplay(expression, variables));
            }
        }
        return rendered;
    }

    /**
     * Resolves a single template string for display.
     * Each {@code {{ }}} segment is tried independently; unresolvable segments fall back to their raw text.
     *
     * @param template  the raw Pebble template (may be null or contain no expressions)
     * @param variables the variable context (flow-level, or execution-level)
     * @return the display string with as many expressions resolved as possible
     */
    public String resolveForDisplay(String template, Map<String, Object> variables) {
        if (template == null || !template.contains("{{")) {
            return template;
        }

        return resolveSegments(template, variables);
    }

    /**
     * Splits the template into alternating literal and expression segments,
     * renders each expression independently, and stitches the result.
     */
    private String resolveSegments(String template, Map<String, Object> variables) {
        var sb = new StringBuilder();
        var matcher = EXPRESSION_PATTERN.matcher(template);
        var lastEnd = 0;

        while (matcher.find()) {
            // Append the literal text before this expression.
            sb.append(template, lastEnd, matcher.start());

            var segment = matcher.group(0); // the full {{ ... }} block
            sb.append(renderSegment(segment, variables));

            lastEnd = matcher.end();
        }
        // Append any trailing literal text.
        sb.append(template, lastEnd, template.length());
        return sb.toString();
    }

    /**
     * Renders a single {@code {{ expr }}} segment using the display engine.
     * Returns the original {@code segment} text on any failure so unresolvable expressions stay raw.
     */
    private String renderSegment(String segment, Map<String, Object> variables) {
        try {
            var template = displayEngine.getLiteralTemplate(segment);
            var writer = new StringWriter();
            template.evaluate(writer, variables);
            return writer.toString();
        } catch (DisplayUnrenderableException e) {
            // Non-deterministic / IO / non-allowlisted function — keep raw.
            return segment;
        } catch (PebbleException | IOException e) {
            // Missing variable, bad syntax, or other rendering failure — keep raw.
            return segment;
        }
    }
}
