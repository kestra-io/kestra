import moment from "moment-timezone"

/**
 * Formats a KV value into a human-readable string for the read-only viewer.
 * Kept pure (timezone passed in) so it can be unit-tested without the DOM.
 */
export function formatKvValueForDisplay(type: string, value: any, timezone?: string): string {
    if (type === "JSON") {
        return JSON.stringify(value, null, 2)
    }
    if (type === "DATETIME") {
        // Follow Timezone from Settings to display KV of type DATETIME (issue #9428)
        const tz = timezone || moment.tz.guess()
        return moment(value).tz(tz).format()
    }
    return String(value)
}

/**
 * Converts a KV value returned by the API into what the edit form's value control expects.
 */
export function hydrateKvValueForForm(type: string, value: any, timezone?: string): any {
    if (type === "JSON") {
        return JSON.stringify(value)
    }
    if (type === "BOOLEAN") {
        return value
    }
    if (type === "DATETIME") {
        // Follow Timezone from Settings to display KV of type DATETIME (issue #9428)
        return moment(value).tz(timezone || moment.tz.guess()).toDate()
    }
    return String(value)
}

/**
 * Serializes a form value into the ION payload the API infers the KV type back from,
 * so that saving then reopening an entry yields the same type and value.
 */
export function serializeKvValueForSave(type: string, value: any): string {
    if (type === "STRING") {
        // Quoted, so a string that looks like a number or a boolean stays a string.
        return JSON.stringify(value)
    }
    if (type === "DURATION" || type === "JSON") {
        return value || ""
    }
    if (type === "DATETIME") {
        return new Date(value).toISOString()
    }
    if (type === "DATE") {
        return moment(value).format("YYYY-MM-DD")
    }
    return String(value)
}
