import moment from "moment"
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

export function duration(isoString: string) {
    return (
        moment.duration(isoString, moment.ISO_8601 as any).asMilliseconds() / 1000
    )
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