import {stringUtils, durationUtils} from "@kestra-io/design-system"
import moment from "moment-timezone"

export const DATE_FORMAT_STORAGE_KEY = "dateFormat"
export const TIMEZONE_STORAGE_KEY = "timezone"

export const dateFilter = (dateString: string, format?: string) => {
    let f
    if (format === "iso") {
        f = "YYYY-MM-DD HH:mm:ss.SSS"
    } else {
        f = format ?? localStorage.getItem(DATE_FORMAT_STORAGE_KEY) ?? "llll"
    }
    return moment(dateString)
        .tz(localStorage.getItem(TIMEZONE_STORAGE_KEY) ?? moment.tz.guess())
        .format(f)
}

export const afterLastDot = stringUtils.afterLastDot
export const humanDuration = durationUtils.humanDuration
export const duration = durationUtils.duration

export default {
    dateFilter,
    afterLastDot,
    humanDuration,
    duration,
}
