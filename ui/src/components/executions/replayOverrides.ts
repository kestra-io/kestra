import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import type {ReplayOutputServiceOutputReader} from "@kestra-io/kestra-sdk"

export type ParsedOverride = {value?: unknown, error?: "number" | "boolean" | "json"}

/** Strings are quoted so that leading or trailing spaces stay visible. */
export function formatValue(value: unknown): string {
    return value === undefined ? "null" : JSON.stringify(value)
}

export function editableText(value: unknown): string {
    if (value === null || value === undefined) return ""
    return typeof value === "string" ? value : JSON.stringify(value)
}

/** An output has no declared type, so the override takes the type of its current value. */
export function parseOverride(original: unknown, text: string): ParsedOverride {
    const trimmed = text.trim()

    if (text === editableText(original)) {
        return {value: original ?? null}
    }
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

const TASK_SECTIONS = ["tasks", "errors", "finally", "afterExecution"]

export function taskSource(flowSource: string, taskId: string): string | undefined {
    for (const section of TASK_SECTIONS) {
        const block = YAML_UTILS.extractBlock({source: flowSource, section, key: taskId})
        if (block !== undefined) return block
    }
    return undefined
}

/** The first line of a task definition that reads one of the paths, 1-based. */
export function firstReadingLine(source: string, paths: string[]): number | undefined {
    const index = source.split("\n").findIndex(line => paths.some(path => readsPath(line, path)))
    return index === -1 ? undefined : index + 1
}

function readsPath(line: string, path: string): boolean {
    for (let index = line.indexOf(path); index !== -1; index = line.indexOf(path, index + 1)) {
        if (!/[\w-]/.test(line.charAt(index + path.length))) return true
    }
    return false
}

export function groupReaders(readers: ReplayOutputServiceOutputReader[]): {taskId: string, paths: string[]}[] {
    const grouped = new Map<string, string[]>()
    readers.forEach(reader => grouped.set(reader.taskId, [...(grouped.get(reader.taskId) ?? []), reader.path]))
    return [...grouped].map(([taskId, paths]) => ({taskId, paths}))
}
