import {getCurrentInstance} from "vue";
import humanizeDuration, {type Options as HumanizeDurationOptions} from "humanize-duration";
import moment from "moment";
import momentTz from "moment-timezone";

const humanizeDurationLanguages = {
    en: {
        y: () => "y",
        mo: () => "mo",
        w: () => "w",
        d: () => "d",
        h: () => "h",
        m: () => "m",
        s: () => "s",
        ms: () => "ms",
    },
    fr: {
        y: () => "a",
        mo: () => "mo",
        w: () => "se",
        d: () => "j",
        h: () => "h",
        m: () => "m",
        s: () => "s",
        ms: () => "ms",
    },
};

export const DATE_FORMAT_STORAGE_KEY = "dateFormat";
export const TIMEZONE_STORAGE_KEY = "timezone";

export const capitalize = (str: string) => str.charAt(0).toUpperCase() + str.slice(1);

export const dateFilter = (dateString: string, format?: string) => {
    const currentLocale = getCurrentInstance()?.appContext.config.globalProperties.$moment().locale();
    const momentInstance = getCurrentInstance()?.appContext.config.globalProperties.$moment(dateString).locale(currentLocale);
    let f;
    if (format === "iso") {
        f = "YYYY-MM-DD HH:mm:ss.SSS";
    } else {
        f = format ?? localStorage.getItem(DATE_FORMAT_STORAGE_KEY) ?? "llll";
    }
    return momentInstance
        .tz(localStorage.getItem(TIMEZONE_STORAGE_KEY) ?? momentTz.tz.guess())
        .format(f);
};

export function splitFirst(str: string, separator: string) {
    return str.split(separator).slice(1).join(separator);
}

export function duration(isoString: string) {
    return (
        moment.duration(isoString, moment.ISO_8601 as any).asMilliseconds() / 1000
    );
}

export function humanDuration(
    value: number | string,
    options?: HumanizeDurationOptions & {languages?: any},
) {
    options = options || {maxDecimalPoints: 2};
    options.spacer = "";
    options.language = localStorage.getItem("lang") || "en";
    options.languages = humanizeDurationLanguages;
    options.largest = 2;

    if (typeof value !== "number") {
        value = duration(value);
    }

    return humanizeDuration(value * 1000, options).replace(/\.([0-9])s$/i, ".$10s");
}

export function afterLastDot(str: string) {
    return str.split(".").pop();
}

export function distinctFilter(value: any, index: number, array: any[]) {
    return array.indexOf(value) === index;
}

export function sanitizeForMarkdown(str: string): string {
    return str
        .replace(/(```)(?:bash|yaml|js|console|json)(\n) *([\s\S]*?```)/g, "$1$2$3")
        .replace(/(?<!:):(?![: /])/g, ": ");
}

export default {
    capitalize,
    dateFilter,
    splitFirst,
    duration,
    humanDuration,
    afterLastDot,
    distinctFilter,
    sanitizeForMarkdown,
};
