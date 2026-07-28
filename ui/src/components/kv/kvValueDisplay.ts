import moment from "moment-timezone";

/**
 * Formats a KV value into a human-readable string for the read-only viewer.
 * Kept pure (timezone passed in) so it can be unit-tested without the DOM.
 */
export function formatKvValueForDisplay(type: string, value: any, timezone?: string): string {
    if (type === "JSON") {
        return JSON.stringify(value, null, 2);
    }
    if (type === "DATETIME") {
        // Follow Timezone from Settings to display KV of type DATETIME (issue #9428)
        const tz = timezone || moment.tz.guess();
        return moment(value).tz(tz).format();
    }
    return String(value);
}
