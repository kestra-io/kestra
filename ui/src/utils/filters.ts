import * as Utils from "./utils"
import {dateUtils, durationUtils} from "@kestra-io/design-system"
import type {Options as HumanizeDurationOptions} from "humanize-duration"

export function humanizeDuration (value:number | string, options?: HumanizeDurationOptions) {
    return durationUtils.humanDuration(value, options)
}
export function humanizeNumber (value:number | string) {
    // Metric values are doubles: keep their decimals, and up to 15 significant digits
    // so very small values such as 5e-7 are not rounded to 0.
    return Number(value).toLocaleString(Utils.getLanguageTag(), {maximumSignificantDigits: 15})
}
export function cap (value:string) {
    return value ? value.toString().capitalize() : ""
}
export function lower (value:string) {
    return value ? value.toString().toLowerCase() : ""
}
/**
 * Formats an instant in the timezone and date format from Settings.
 *
 * Accepts what `dayjs` accepts: an ISO string, an epoch millisecond timestamp, or a `Date`.
 * Callers must not pre-serialise a timestamp with `toISOString()` — that throws on a
 * non-finite value, whereas this degrades to the string "Invalid Date".
 *
 * @param dateValue the instant to format
 * @param format    a dayjs format, or "iso" for `YYYY-MM-DD HH:mm:ss.SSS`; defaults to the
 *                  user's stored date format
 */
export function date (dateValue:string | number | Date, format?:string) {
    return dateUtils.dateFilter(dateValue, format)
}

export interface FilterObject{
    field: string;
    value: string | string[];
    operation: string;
}

export default {
    humanizeDuration,
    humanizeNumber,
    cap,
    lower,
    date,
}


