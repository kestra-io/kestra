import moment from "moment-timezone"

export type KvFormValue = string | number | boolean | Date | undefined

/**
 * Formats a KV value into a human-readable string for the read-only viewer.
 * Kept pure (timezone passed in) so it can be unit-tested without the DOM.
 */
export function formatKvValueForDisplay(type: string, value: unknown, timezone?: string): string {
    if (type === "JSON") {
        return JSON.stringify(value, null, 2) ?? ""
    }
    if (type === "DATETIME") {
        // Follow Timezone from Settings to display KV of type DATETIME (issue #9428)
        const tz = timezone || moment.tz.guess()
        return moment(value as string | number | Date).tz(tz).format()
    }
    return String(value)
}

/**
 * Converts a KV value returned by the API into what the edit form's value control expects.
 */
export function hydrateKvValueForForm(type: "DATETIME", value: unknown, timezone?: string): Date
export function hydrateKvValueForForm(type: "BOOLEAN", value: unknown, timezone?: string): boolean
export function hydrateKvValueForForm(type: string, value: unknown, timezone?: string): KvFormValue
export function hydrateKvValueForForm(type: string, value: unknown, timezone?: string): KvFormValue {
    if (type === "JSON") {
        return JSON.stringify(value) ?? ""
    }
    if (type === "BOOLEAN") {
        return value as boolean
    }
    if (type === "DATETIME") {
        // Follow Timezone from Settings to display KV of type DATETIME (issue #9428)
        return moment(value as string | number | Date).tz(timezone || moment.tz.guess()).toDate()
    }
    return String(value)
}

/**
 * Serializes a form value into the ION payload the API infers the KV type back from,
 * so that saving then reopening an entry yields the same type and value.
 */
export function serializeKvValueForSave(type: string, value: KvFormValue): string {
    if (type === "STRING") {
        // Quoted, so a string that looks like a number or a boolean stays a string.
        return JSON.stringify(value)
    }
    if (type === "DURATION" || type === "JSON") {
        return typeof value === "string" ? value : ""
    }
    if (type === "DATETIME") {
        return new Date(value as string | number | Date).toISOString()
    }
    if (type === "DATE") {
        const dateValue = typeof value === "string" || typeof value === "number" || value instanceof Date ? value : undefined
        return moment(dateValue).format("YYYY-MM-DD")
    }
    return String(value)
}
