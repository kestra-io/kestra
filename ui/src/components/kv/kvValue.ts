import {dateUtils, dayjs} from "@kestra-io/design-system"
import type {KvType} from "@kestra-io/kestra-sdk"

export type KvFormValue = string | number | boolean | Date | undefined

function toDateInput(value: unknown): string | number | Date | undefined {
    return typeof value === "string" || typeof value === "number" || value instanceof Date ? value : undefined
}

/**
 * Formats a KV value into a human-readable string for the read-only viewer.
 * Kept pure (timezone passed in) so it can be unit-tested without the DOM.
 */
export function formatKvValueForDisplay(type: KvType, value: unknown, timezone?: string): string {
    if (type === "JSON") {
        return JSON.stringify(value, null, 2) ?? ""
    }
    if (type === "DATETIME") {
        // Follow Timezone from Settings to display KV of type DATETIME (issue #9428)
        const tz = timezone || dateUtils.currentTimezone()
        return dayjs(toDateInput(value)).tz(tz).format()
    }
    return String(value)
}

/**
 * Converts a KV value returned by the API into what the edit form's value control expects.
 */
export function hydrateKvValueForForm(type: KvType, value: unknown, timezone?: string): KvFormValue {
    if (type === "JSON") {
        return JSON.stringify(value) ?? ""
    }
    if (type === "BOOLEAN") {
        return value === true
    }
    if (type === "DATETIME") {
        // Follow Timezone from Settings to display KV of type DATETIME (issue #9428)
        return dayjs(toDateInput(value)).tz(timezone || dateUtils.currentTimezone()).toDate()
    }
    return String(value)
}

/**
 * Serializes a form value into the ION payload the API infers the KV type back from,
 * so that saving then reopening an entry yields the same type and value.
 */
export function serializeKvValueForSave(type: KvType, value: KvFormValue): string {
    if (type === "STRING") {
        // Quoted, so a string that looks like a number or a boolean stays a string.
        return JSON.stringify(value) ?? ""
    }
    if (type === "DURATION" || type === "JSON") {
        return typeof value === "string" ? value : ""
    }
    if (type === "DATETIME") {
        return new Date(toDateInput(value) ?? "").toISOString()
    }
    if (type === "DATE") {
        return dayjs(toDateInput(value)).format("YYYY-MM-DD")
    }
    return String(value)
}
