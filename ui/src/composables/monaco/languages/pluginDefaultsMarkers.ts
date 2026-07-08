import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"

/**
 * A plugin default entry, as declared in a flow's `pluginDefaults` block or
 * inherited from the namespace / global configuration. `values` holds the
 * properties the default supplies for tasks/triggers matching `type`.
 */
export interface EffectiveDefault {
    type?: string;
    values?: Record<string, unknown> | null;
}

/**
 * Minimal shape of a Monaco marker this filter reasons about. Kept structural
 * (not tied to `monaco.editor.IMarkerData`) so the helper stays pure and
 * unit-testable without Monaco.
 */
export interface MarkerLike {
    message: string;
    startLineNumber: number;
    startColumn: number;
}

// monaco-yaml emits missing-required-property diagnostics as: Missing property "x".
const MISSING_PROPERTY_MESSAGE = /^Missing property "(.+)"\.$/

interface TypedMap {
    type?: unknown;
    range: [number, number, number];
}

/**
 * Convert a 1-based Monaco `(line, column)` position into a 0-based offset in
 * `source`, so it can be matched against YAML node ranges.
 */
function positionToOffset(source: string, lineNumber: number, column: number): number {
    const lines = source.split("\n")
    let offset = 0
    for (let i = 0; i < lineNumber - 1 && i < lines.length; i++) {
        offset += lines[i].length + 1 // +1 for the newline
    }
    return offset + (column - 1)
}

/**
 * Resolve the `type` of the task/trigger mapping enclosing the given source
 * offset. Uses {@link YAML_UTILS.extractFieldFromMaps} to collect every mapping
 * carrying a `type` field with its full node range, then returns the innermost
 * one containing the offset (largest start still enclosing it). Returns
 * undefined when no typed mapping encloses the offset.
 */
function findEnclosingTaskType(source: string, offset: number): string | undefined {
    try {
        const typed = YAML_UTILS.extractFieldFromMaps(source, "type") as TypedMap[]
        const enclosing = typed
            .filter((entry) => entry.range[0] <= offset && offset <= entry.range[2])
            .sort((a, b) => b.range[0] - a.range[0])[0]
        return typeof enclosing?.type === "string" ? enclosing.type : undefined
    } catch {
        return undefined
    }
}

/**
 * A default covers a task when its `type` matches the task `type` either
 * exactly or by prefix — the same semantics as the backend
 * `PluginDefaultService.defaults()` matcher.
 */
function defaultMatchesType(defaultType: string, taskType: string): boolean {
    return defaultType === taskType || taskType.startsWith(defaultType)
}

/**
 * True when at least one effective default that matches `taskType` supplies
 * `property` in its `values`.
 */
export function isPropertyCoveredByDefaults(
    property: string,
    taskType: string,
    defaults: EffectiveDefault[],
): boolean {
    return defaults.some((def) => {
        if (!def?.type || !defaultMatchesType(def.type, taskType)) {
            return false
        }
        return !!def.values && Object.prototype.hasOwnProperty.call(def.values, property)
    })
}

/**
 * Remove `Missing property "x"` markers that are false positives because an
 * effective plugin default (flow-level, namespace-level, or global) supplies
 * `x` for the enclosing task's `type`. All other markers are preserved.
 *
 * Pure and idempotent: re-running it on its own output removes nothing, which
 * the configurator relies on to avoid a re-publish loop.
 */
export function filterDefaultsCoveredMarkers<T extends MarkerLike>(
    markers: T[],
    source: string,
    defaults: EffectiveDefault[],
): T[] {
    if (!markers.length || !defaults.length || !source.length) {
        return markers
    }

    return markers.filter((marker) => {
        const match = MISSING_PROPERTY_MESSAGE.exec(marker.message)
        if (!match) {
            return true
        }
        const property = match[1]
        const offset = positionToOffset(source, marker.startLineNumber, marker.startColumn)
        const taskType = findEnclosingTaskType(source, offset)
        if (!taskType) {
            return true
        }
        // Drop the marker only when a default actually covers the property.
        return !isPropertyCoveredByDefaults(property, taskType, defaults)
    })
}
