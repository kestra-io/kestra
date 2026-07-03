import * as Utils from "./utils"
import {storageKeys} from "../utils/constants"
import moment from "moment-timezone"
import {durationUtils} from "@kestra-io/design-system"

export function humanizeDuration (value:string, options?:any) {
    return durationUtils.humanDuration(value, options)
}
export function humanizeNumber (value:string) {
    return parseInt(value).toLocaleString(Utils.getLang())
}
export function cap (value:string) {
    return value ? value.toString().capitalize() : ""
}
export function lower (value:string) {
    return value ? value.toString().toLowerCase() : ""
}
export function date (dateString:string, format?:string) {
    const currentLocale = moment().locale()
    const momentInstance = moment(dateString).locale(currentLocale)
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


