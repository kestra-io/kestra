import Utils from "./utils";
import {DATE_FORMAT_STORAGE_KEY, TIMEZONE_STORAGE_KEY} from "../components/settings/BasicSettings.vue";
import moment from "moment-timezone";

export function invisibleSpace (value:string) {
        return value.replace(/\./g, "\u200B" + ".");
}
export function humanizeDuration (value:string, options?:any) {
    return Utils.humanDuration(value, options);
}
export function humanizeNumber (value:string) {
    return parseInt(value).toLocaleString(Utils.getLanguageTag());
}
export function cap (value:string) {
    return value ? value.toString().capitalize() : "";
}
export function lower (value:string) {
    return value ? value.toString().toLowerCase() : "";
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
    // The app's `$moment` global is this same moment-timezone singleton, so reading it through
    // getCurrentInstance() only made the helper unusable outside a component render.
    const currentLocale = moment().locale();
    const momentInstance = moment(dateValue).locale(currentLocale);
    let f;
    if (format === "iso") {
        f = "YYYY-MM-DD HH:mm:ss.SSS";
    } else {
        f = format ?? localStorage.getItem(DATE_FORMAT_STORAGE_KEY) ?? "llll";
    }
    // Apply timezone and format using the correct locale
    return momentInstance
        .tz(localStorage.getItem(TIMEZONE_STORAGE_KEY) ?? moment.tz.guess())
        .format(f);
}

export default {
    invisibleSpace,
    humanizeDuration,
    humanizeNumber,
    cap,
    lower,
    date
}


