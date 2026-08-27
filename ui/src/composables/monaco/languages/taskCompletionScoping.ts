import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"

type TaskLike = Record<string, unknown>

function isTaskLike(value: unknown): value is TaskLike {
    return (
        typeof value === "object" &&
        value !== null &&
        typeof (value as TaskLike).id === "string" &&
        typeof (value as TaskLike).type === "string"
    )
}

function probeTaskLike(source: string, cursorIndex: number): TaskLike | undefined {
    const safeCursorIndex = Math.max(0, Math.min(cursorIndex - 1, source.length - 1))
    const probeIndexes = [safeCursorIndex]
    let previousNonWhitespace = safeCursorIndex
    while (previousNonWhitespace > 0 && /\s/.test(source.charAt(previousNonWhitespace))) {
        previousNonWhitespace--
    }
    if (previousNonWhitespace !== safeCursorIndex) {
        probeIndexes.push(previousNonWhitespace)
    }

    for (const probeIndex of probeIndexes) {
        const localized = YAML_UTILS.localizeElementAtIndex(source, probeIndex)
        const candidates = [...(localized?.parents ?? []), localized?.value]

        for (let i = candidates.length - 1; i >= 0; i--) {
            const candidate = candidates[i]
            if (isTaskLike(candidate)) {
                return candidate
            }
        }
    }

    return undefined
}

function blankLineAtCursor(source: string, cursorIndex: number): string {
    const lineStart = source.lastIndexOf("\n", Math.max(0, cursorIndex - 1)) + 1
    const nextNewline = source.indexOf("\n", lineStart)
    const lineEnd = nextNewline === -1 ? source.length : nextNewline
    return source.slice(0, lineStart) + " ".repeat(lineEnd - lineStart) + source.slice(lineEnd)
}

/**
 * Resolves the task map enclosing the cursor by localizing the YAML node at (and just before) the
 * cursor and walking its ancestry outwards to the nearest node that has both an `id` and a `type`.
 */
export function findTaskLikeAtCursor({source, cursorIndex}: {source: string; cursorIndex: number}): TaskLike | undefined {
    if (!source.length) {
        return undefined
    }

    try {
        // A half-typed property key localizes to nothing, so retry with the cursor's line blanked out
        // (same length, so offsets still line up) to resolve from the surrounding task instead.
        return probeTaskLike(source, cursorIndex)
            ?? probeTaskLike(blankLineAtCursor(source, cursorIndex), cursorIndex)
    } catch {
        return undefined
    }
}

/** Returns the `type` of the task enclosing the cursor, or undefined when the cursor is not inside a task. */
export function taskTypeAtCursor(params: {source: string; cursorIndex: number}): string | undefined {
    const task = findTaskLikeAtCursor(params)
    return typeof task?.type === "string" ? task.type : undefined
}

/** Of the given required properties, those the task enclosing the cursor has not set yet. */
export function filterMissingRequiredTaskProperties({
    source,
    cursorIndex,
    requiredProperties,
}: {
    source: string;
    cursorIndex: number;
    requiredProperties: string[];
}): string[] {
    if (!requiredProperties.length || !source.length) {
        return []
    }

    const task = findTaskLikeAtCursor({source, cursorIndex})
    if (!task) {
        return requiredProperties
    }

    return requiredProperties.filter(
        (property) => !Object.prototype.hasOwnProperty.call(task, property),
    )
}

/**
 * Drops property-key suggestions that do not belong to the resolved task type. Non-property
 * suggestions are always kept, and an empty/undefined key set fails open (returns everything).
 */
export function scopePropertySuggestionsToTaskType<T extends {label: string; kind?: number}>({
    suggestions,
    validPropertyKeys,
    propertyKind,
}: {
    suggestions: T[];
    validPropertyKeys: string[] | undefined;
    propertyKind: number;
}): T[] {
    if (!validPropertyKeys?.length) {
        return suggestions
    }

    const valid = new Set(validPropertyKeys)
    return suggestions.filter((suggestion) => suggestion.kind !== propertyKind || valid.has(suggestion.label))
}
