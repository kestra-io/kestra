import type {ReplayOverrideServiceReplayValue} from "@kestra-io/kestra-sdk"

export type ReplayValue = ReplayOverrideServiceReplayValue

export type ParsedOverride = {value?: unknown, error?: "number" | "boolean" | "json"}

export function isOverridable(value: ReplayValue): boolean {
    return value.kind === "EXPRESSION"
}

export function asExpression(expression: string | undefined): string {
    return `{{ ${expression ?? ""} }}`
}

/** Strings are quoted so that leading or trailing spaces stay visible. */
export function formatValue(value: unknown): string {
    return value === undefined ? "null" : JSON.stringify(value)
}

export function editableText(value: unknown): string {
    if (value === null || value === undefined) return ""
    return typeof value === "string" ? value : JSON.stringify(value)
}

/** An expression has no declared type, so the override takes the type of what it rendered. */
export function parseOverride(replayValue: ReplayValue, text: string): ParsedOverride {
    const original: unknown = replayValue.value
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
