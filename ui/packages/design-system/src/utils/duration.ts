import dayjs from "../date/dayjs"
import humanizeDuration, {type Options as HumanizeDurationOptions} from "humanize-duration"

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
    "zh_CN": {
        y: () => "年",
        mo: () => "月",
        w: () => "周",
        d: () => "天",
        h: () => "小时",
        m: () => "分钟",
        s: () => "秒",
        ms: () => "毫秒",
    },
}

const ISO_8601_DURATION = /^-?P(?!$)(\d+(?:\.\d+)?Y)?(\d+(?:\.\d+)?M)?(\d+(?:\.\d+)?W)?(\d+(?:\.\d+)?D)?(T(?!$)(\d+(?:\.\d+)?H)?(\d+(?:\.\d+)?M)?(\d+(?:\.\d+)?S)?)?$/

/** Whether the text is an ISO 8601 duration; dayjs coerces anything else to null rather than failing. */
export function isValidDuration(value: string): boolean {
    return ISO_8601_DURATION.test(value)
}

export function duration(isoString: string) {
    return isValidDuration(isoString)
        ? dayjs.duration(isoString).asMilliseconds() / 1000
        : 0
}

export function humanDuration(
    value: number | string,
    options?: HumanizeDurationOptions & { languages?: any },
) {
    options = options || {maxDecimalPoints: 2}
    options.spacer = ""
    options.language = localStorage.getItem("lang") || "en"
    options.languages = humanizeDurationLanguages
    options.largest = 2

    if (typeof value !== "number") {
        value = duration(value)
    }

    return humanizeDuration(value * 1000, options).replace(
        /\.([0-9])s$/i,
        ".$10s",
    )
}