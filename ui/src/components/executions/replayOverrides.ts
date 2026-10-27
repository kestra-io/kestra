import type {ReplayOverrideServiceReplayValue} from "@kestra-io/kestra-sdk"

export type ReplayValue = ReplayOverrideServiceReplayValue

export type ParsedOverride = {value?: unknown, error?: "number" | "boolean" | "date" | "datetime" | "json"}

const NUMBER_INPUTS = new Set(["INT", "FLOAT"])
const BOOLEAN_INPUTS = new Set(["BOOLEAN", "BOOL"])
const JSON_INPUTS = new Set(["JSON", "ARRAY", "MULTISELECT"])

export function isOverridable(value: ReplayValue): boolean {
    return value.kind === "INPUT" || value.kind === "OUTPUT"
}

/** Strings are quoted so that leading or trailing spaces stay visible. */
export function formatValue(value: unknown): string {
    return value === undefined ? "null" : JSON.stringify(value)
}

export function editableText(value: unknown): string {
    if (value === null || value === undefined) return ""
    return typeof value === "string" ? value : JSON.stringify(value)
}

export function parseOverride(replayValue: ReplayValue, text: string): ParsedOverride {
    const original: unknown = replayValue.value
    const inputType = replayValue.inputType ?? ""
    const trimmed = text.trim()

    if (text === editableText(original)) {
        return {value: original ?? null}
    }

    if (inputType === "DATE") {
        return /^\d{4}-\d{2}-\d{2}$/.test(trimmed) ? {value: trimmed} : {error: "date"}
    }
    if (inputType === "DATETIME") {
        return Number.isNaN(Date.parse(trimmed)) ? {error: "datetime"} : {value: trimmed}
    }
    if (typeof original === "number" || NUMBER_INPUTS.has(inputType)) {
        const number = Number(trimmed)
        return trimmed === "" || !Number.isFinite(number) ? {error: "number"} : {value: number}
    }
    if (typeof original === "boolean" || BOOLEAN_INPUTS.has(inputType)) {
        return trimmed === "true" || trimmed === "false" ? {value: trimmed === "true"} : {error: "boolean"}
    }
    if ((original !== null && typeof original === "object") || JSON_INPUTS.has(inputType)) {
        try {
            return {value: JSON.parse(trimmed)}
        } catch {
            return {error: "json"}
        }
    }
    return {value: text}
}
