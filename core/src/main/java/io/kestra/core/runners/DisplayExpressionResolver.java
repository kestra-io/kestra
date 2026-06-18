package io.kestra.core.runners;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.pebble.DisplayUnrenderableException;
import io.kestra.core.runners.pebble.PebbleEngineFactory;
import io.kestra.core.serializers.JacksonMapper;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.error.PebbleException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves Pebble expression strings for display purposes only — not for task execution.
 *
 * <p>Resolution rules (see architecture decision in issue #16874):
 * <ul>
 *   <li>{@code secret()} → {@code [secret: KEY]} (never invoked)</li>
 *   <li>{@code env()} → {@code [env: NAME]} (never invoked)</li>
 *   <li>Non-deterministic / IO functions ({@code now()}, {@code uuid()}, {@code read()}, …) → kept raw</li>
 *   <li>{@code vars.*}, {@code flow.*}, {@code globals.*}, {@code kv()} → resolved</li>
 *   <li>{@code inputs.*}, {@code outputs.*}, {@code execution.*} → resolved when an execution is present; raw otherwise</li>
 * </ul>
 *
 * <p>Segment-level resolution: mixed strings like {@code "{{ vars.region }}-{{ now() }}"}
 * produce {@code "us-east-1-{{ now() }}"}.
 */
@Singleton
@Slf4j
public class DisplayExpressionResolver {

    // Matches a single {{ ... }} expression block.
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\{\\{(.*?)}}");

    // Matches {% raw %} ... {% endraw %} blocks to be preserved verbatim.
    private static final Pattern RAW_PATTERN = Pattern.compile("(\\{%-*\\s*raw\\s*-*%}(.*?)\\{%-*\\s*endraw\\s*-*%})");

    private final PebbleEngine displayEngine;

    @Inject
    public DisplayExpressionResolver(PebbleEngineFactory pebbleEngineFactory) {
        this.displayEngine = pebbleEngineFactory.createForDisplay();
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

        // Preserve {% raw %} blocks by replacing them with stable placeholders.
        var rawReplacements = new LinkedHashMap<String, String>();
        var withoutRawBlocks = replaceRawBlocks(template, rawReplacements);

        var result = resolveSegments(withoutRawBlocks, variables);

        // Restore {% raw %} blocks.
        for (var entry : rawReplacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Serializes a Task to a property map and recursively resolves every string leaf for display.
     *
     * @param task      the task whose properties to resolve
     * @param variables the display variable context
     * @return a new map with the same structure as the task's JSON representation, strings resolved
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> resolveProperties(Task task, Map<String, Object> variables) {
        var rawMap = JacksonMapper.toMap(task);
        return (Map<String, Object>) resolveValue(rawMap, variables);
    }

    // --- private helpers ---

    private String replaceRawBlocks(String template, Map<String, String> replacements) {
        var matcher = RAW_PATTERN.matcher(template);
        return matcher.replaceAll(match ->
        {
            // A random token, not a predictable counter, so user content can never collide with it.
            var placeholder = "__kestra_raw_" + UUID.randomUUID().toString().replace("-", "") + "__";
            replacements.put(placeholder, match.group(1));
            // Matcher.replaceAll treats $ and \ in the replacement specially; our token has neither,
            // but quote defensively in case the format ever changes.
            return Matcher.quoteReplacement(placeholder);
        });
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
            // Non-deterministic / IO function — keep raw.
            return segment;
        } catch (PebbleException | IOException e) {
            // Missing variable, bad syntax, or other rendering failure — keep raw.
            return segment;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object resolveValue(Object value, Map<String, Object> variables) {
        if (value instanceof Map map) {
            var resolved = new LinkedHashMap<String, Object>(map.size());
            for (var entry : ((Map<String, Object>) map).entrySet()) {
                resolved.put(entry.getKey(), resolveValue(entry.getValue(), variables));
            }
            return resolved;
        } else if (value instanceof List list) {
            var resolved = new ArrayList<>(list.size());
            for (var item : (List<?>) list) {
                resolved.add(resolveValue(item, variables));
            }
            return resolved;
        } else if (value instanceof String string) {
            return resolveForDisplay(string, variables);
        }
        // Primitives, booleans, numbers — no rendering needed.
        return value;
    }
}
