import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"

type TaskLike = Record<string, unknown>

function isTaskLike(value: unknown): value is TaskLike {
    return (
        typeof value === "object" &&
        value !== null &&
        typeof (value as TaskLike).id === "string" &&
        typeof (value as TaskLike).type === "string"
    )
}

function isMap(value: unknown): value is TaskLike {
    return typeof value === "object" && value !== null && !Array.isArray(value)
}

function probeIndexes(source: string, cursorIndex: number): number[] {
    const safeCursorIndex = Math.max(0, Math.min(cursorIndex - 1, source.length - 1))
    const indexes = [safeCursorIndex]
    let previousNonWhitespace = safeCursorIndex
    while (previousNonWhitespace > 0 && /\s/.test(source.charAt(previousNonWhitespace))) {
        previousNonWhitespace--
    }
    if (previousNonWhitespace !== safeCursorIndex) {
        indexes.push(previousNonWhitespace)
    }
    return indexes
}

function probe<T>(source: string, cursorIndex: number, pick: (candidates: unknown[]) => T | undefined): T | undefined {
    for (const probeIndex of probeIndexes(source, cursorIndex)) {
        const localized = YAML_UTILS.localizeElementAtIndex(source, probeIndex)
        const found = pick([...(localized?.parents ?? []), localized?.value])
        if (found !== undefined) {
            return found
        }
    }
    return undefined
}

function innermostTaskIndex(candidates: unknown[]): number {
    for (let i = candidates.length - 1; i >= 0; i--) {
        if (isTaskLike(candidates[i])) {
            return i
        }
    }
    return -1
}

function probeTaskLike(source: string, cursorIndex: number): TaskLike | undefined {
    return probe(source, cursorIndex, (candidates) => {
        const taskIndex = innermostTaskIndex(candidates)
        return taskIndex === -1 ? undefined : (candidates[taskIndex] as TaskLike)
    })
}

function probeTaskOwningCursor(source: string, cursorIndex: number): TaskLike | undefined {
    return probe(source, cursorIndex, (candidates) => {
        const taskIndex = innermostTaskIndex(candidates)
        if (taskIndex === -1) {
            return undefined
        }
        // A map between the task and the cursor means the cursor sits in a sub-map (`retry:`,
        // `taskRunner:`…) whose keys come from its own schema, so the task must not scope them.
        return candidates.slice(taskIndex + 1).some(isMap)
            ? undefined
            : (candidates[taskIndex] as TaskLike)
    })
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

/**
 * The type, and pinned version if any, of the task whose body *directly* encloses the cursor.
 * Undefined inside a nested sub-map such as `retry:` or `taskRunner:`, whose keys are drawn from
 * their own schema rather than the task's, so scoping them against the task would be wrong.
 */
export function taskIdentityAtCursor(
    {source, cursorIndex}: {source: string; cursorIndex: number},
): {type: string; version?: string} | undefined {
    if (!source.length) {
        return undefined
    }

    try {
        const task = probeTaskOwningCursor(source, cursorIndex)
            ?? probeTaskOwningCursor(blankLineAtCursor(source, cursorIndex), cursorIndex)
        if (!isTaskLike(task)) {
            return undefined
        }

        return {
            type: task.type as string,
            version: typeof task.version === "string" ? task.version : undefined,
        }
    } catch {
        return undefined
    }
}

/** Returns the `type` of the task whose body directly encloses the cursor. */
export function taskTypeAtCursor(params: {source: string; cursorIndex: number}): string | undefined {
    return taskIdentityAtCursor(params)?.type
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
