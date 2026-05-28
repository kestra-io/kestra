import {getCurrentInstance} from "vue"
import momentTz from "moment-timezone"

export const DATE_FORMAT_STORAGE_KEY = "dateFormat"
export const TIMEZONE_STORAGE_KEY = "timezone"

export const dateFilter = (dateString: string, format?: string) => {
    const currentLocale = getCurrentInstance()?.appContext.config.globalProperties.$moment().locale()
    const momentInstance = getCurrentInstance()?.appContext.config.globalProperties.$moment(dateString).locale(currentLocale)
    let f
    if (format === "iso") {
        f = "YYYY-MM-DD HH:mm:ss.SSS"
    } else {
        f = format ?? localStorage.getItem(DATE_FORMAT_STORAGE_KEY) ?? "llll"
    }
    // Apply timezone and format using the correct locale
    return momentInstance
        .tz(localStorage.getItem(TIMEZONE_STORAGE_KEY) ?? momentTz.tz.guess())
        .format(f)
}