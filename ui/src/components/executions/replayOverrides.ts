import {cloneDeep, getPath, isPlainObject, setPath} from "@kestra-io/design-system"

export interface OutputPath {
    path: string
    taskId: string
    keys: string[]
}

export type ParsedOverride = {value?: unknown, error?: "number" | "boolean" | "json"}

const CHAIN = /(?<![\w.'"$])outputs((?:\s*\.\s*[A-Za-z_]\w*|\s*\[\s*(?:'[^'\]]*'|"[^"\]]*")\s*\])+)(\s*[[(.])?/g
const SEGMENT = /\.\s*([A-Za-z_]\w*)|\[\s*'([^']*)'\s*\]|\[\s*"([^"]*)"\s*\]/g

function isTask(value: unknown): value is Record<string, unknown> {
    return isPlainObject(value) && typeof value.id === "string" && typeof value.type === "string"
}

export function findTask(node: unknown, taskId: string): Record<string, unknown> | undefined {
    if (isTask(node) && node.id === taskId) return node
    const children = Array.isArray(node) ? node : isPlainObject(node) ? Object.values(node) : []
    for (const child of children) {
        const found = findTask(child, taskId)
        if (found) return found
    }
    return undefined
}

/** The templates of the task's own properties, not of the tasks it holds. */
export function ownTemplates(task: Record<string, unknown>): string[] {
    const templates: string[] = []
    const visit = (value: unknown, root: boolean) => {
        if (!root && isTask(value)) return
        if (typeof value === "string") templates.push(value)
        else if (Array.isArray(value)) value.forEach(item => visit(item, false))
        else if (isPlainObject(value)) Object.values(value).forEach(item => visit(item, false))
    }
    visit(task, true)
    return templates
}

// Pebble is only parsed on the server; this scan keeps to `{{ }}` and skips `{% %}` and `{# #}`.
function printBlocks(template: string): string[] {
    const blocks: string[] = []
    let index = 0
    while (index < template.length) {
        const open = template.slice(index).search(/\{[{%#]/)
        if (open === -1) break
        const start = index + open
        const close = {"{": "}}", "%": "%}", "#": "#}"}[template[start + 1] as "{" | "%" | "#"]
        const end = template.indexOf(close, start + 2)
        if (end === -1) break
        if (close === "}}") blocks.push(template.slice(start + 2, end))
        index = end + 2
    }
    return blocks
}

/** The output paths read with literal keys only; a chain followed by a dynamic key or a call is left out entirely. */
export function readPaths(templates: string[]): OutputPath[] {
    const paths = new Map<string, OutputPath>()
    for (const block of templates.flatMap(printBlocks)) {
        for (const match of block.matchAll(CHAIN)) {
            if (match[2]) continue
            const segments = [...match[1].matchAll(SEGMENT)].map(segment => segment[1] ?? segment[2] ?? segment[3])
            const path = "outputs" + segments.map(segment => /^[A-Za-z_]\w*$/.test(segment) ? `.${segment}` : `['${segment}']`).join("")
            paths.set(path, {path, taskId: segments[0], keys: segments.slice(1)})
        }
    }
    return [...paths.values()]
}

/** Applies the new values to a copy of the outputs, shorter paths first so a nested value wins over its parent. */
export function patchedOutputs(outputs: Record<string, unknown>, patches: {keys: string[], value: unknown}[]): Record<string, unknown> {
    let patched = cloneDeep(outputs)
    for (const {keys, value} of [...patches].sort((a, b) => a.keys.length - b.keys.length)) {
        if (keys.length === 0) {
            patched = isPlainObject(value) ? cloneDeep(value) : patched
        } else {
            setPath(patched, keys, value)
        }
    }
    return patched
}

export function valueAt(outputs: Record<string, unknown>, keys: string[]): unknown {
    return keys.length === 0 ? outputs : getPath(outputs, keys)
}

export function editableText(value: unknown): string {
    if (value === null || value === undefined) return ""
    return typeof value === "string" ? value : JSON.stringify(value)
}

/** An output has no declared type, so the new value takes the type of the current one. */
export function parseOverride(original: unknown, text: string): ParsedOverride {
    const trimmed = text.trim()
    if (text === editableText(original)) return {value: original ?? null}
    if (typeof original === "number") {
        const number = Number(trimmed)
        return trimmed === "" || !Number.isFinite(number) ? {error: "number"} : {value: number}
    }
    if (typeof original === "boolean") {
        return trimmed === "true" || trimmed === "false" ? {value: trimmed === "true"} : {error: "boolean"}
    }
    if (original !== null && typeof original === "object") {
        try {
            return {value: JSON.parse(trimmed)}
        } catch {
            return {error: "json"}
        }
    }
    return {value: text}
}
