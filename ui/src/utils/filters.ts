import * as Utils from "./utils"
import {storageKeys} from "../utils/constants"
import moment from "moment-timezone"
import {durationUtils} from "@kestra-io/design-system"

export function humanizeDuration (value:number | string, options?:any) {
    return durationUtils.humanDuration(value, options)
}
export function humanizeNumber (value:string) {
    return parseInt(value).toLocaleString(Utils.getLanguageTag())
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
 * Accepts what `moment` accepts: an ISO string, an epoch millisecond timestamp, or a `Date`.
 * Callers must not pre-serialise a timestamp with `toISOString()` — that throws on a
 * non-finite value, whereas `moment` degrades to the string "Invalid date".
 *
 * @param dateValue the instant to format
 * @param format    a moment format, or "iso" for `YYYY-MM-DD HH:mm:ss.SSS`; defaults to the
 *                  user's stored date format
 */
export function date (dateValue:string | number | Date, format?:string) {
    const currentLocale = moment().locale()
    const momentInstance = moment(dateValue).locale(currentLocale)
    let f
    if (format === "iso") {
        f = "YYYY-MM-DD HH:mm:ss.SSS"
    } else {
        f = format ?? localStorage.getItem(storageKeys.DATE_FORMAT_STORAGE_KEY) ?? "llll"
    }
    // Apply timezone and format using the correct locale
    return momentInstance
        .tz(localStorage.getItem(storageKeys.TIMEZONE_STORAGE_KEY) ?? moment.tz.guess())
        .format(f)
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


