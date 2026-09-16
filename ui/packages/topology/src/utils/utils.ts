import {stringUtils, durationUtils, dateUtils} from "@kestra-io/design-system"

export const DATE_FORMAT_STORAGE_KEY = dateUtils.DATE_FORMAT_STORAGE_KEY
export const TIMEZONE_STORAGE_KEY = dateUtils.TIMEZONE_STORAGE_KEY

export const dateFilter = dateUtils.dateFilter
export const afterLastDot = stringUtils.afterLastDot
export const humanDuration = durationUtils.humanDuration
export const duration = durationUtils.duration

export default {
    dateFilter,
    afterLastDot,
    humanDuration,
    duration,
}
