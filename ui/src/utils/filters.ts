import Utils from "./utils";
import {getCurrentInstance} from "vue";
import {DATE_FORMAT_STORAGE_KEY, TIMEZONE_STORAGE_KEY} from "../components/settings/BasicSettings.vue";
import moment from "moment-timezone";

export function invisibleSpace (value:string) {
        return value.replace(/\./g, "\u200B" + ".");
}
export function humanizeDuration (value:string, options?:any) {
    return Utils.humanDuration(value, options);
}
export function humanizeNumber (value:string) {
    return parseInt(value).toLocaleString(Utils.getLang());
}
export function cap (value:string) {
    return value ? value.toString().capitalize() : "";
}
export function lower (value:string) {
    return value ? value.toString().toLowerCase() : "";
}
export function date (dateString:string, format?:string) {
    const currentLocale = getCurrentInstance()?.appContext.config.globalProperties.$moment().locale();
    const momentInstance = getCurrentInstance()?.appContext.config.globalProperties.$moment(dateString).locale(currentLocale);
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


