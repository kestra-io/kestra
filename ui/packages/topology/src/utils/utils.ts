import {stringUtils, durationUtils, dateUtils} from "@kestra-io/design-system"

export const DATE_FORMAT_STORAGE_KEY = dateUtils.DATE_FORMAT_STORAGE_KEY
export const TIMEZONE_STORAGE_KEY = dateUtils.TIMEZONE_STORAGE_KEY

export const dateFilter = dateUtils.dateFilter
export const afterLastDot = stringUtils.afterLastDot
export const humanDuration = durationUtils.humanDuration
export const duration = durationUtils.duration

// Every core and plugin task shares the same `io.kestra.plugin.` prefix; dropping it leaves the
// part that actually identifies the task's type.
export function shortPluginType(cls?: string): string {
    return (cls ?? "").replace(/^io\.kestra\.plugin\./, "")
}

export default {
    dateFilter,
    afterLastDot,
    humanDuration,
    duration,
    shortPluginType,
}
