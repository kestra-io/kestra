import {afterEach, beforeEach, describe, expect, it} from "vitest"
import {dayjs} from "@kestra-io/design-system"
import {date, humanizeDuration, humanizeNumber} from "../../../src/utils/filters"
import {storageKeys} from "../../../src/utils/constants"

describe("humanizeNumber", () => {
    afterEach(() => {
        localStorage.removeItem("lang")
    })

    it("formats with the default language when none is stored", () => {
        expect(humanizeNumber("1234567")).toBe((1234567).toLocaleString("en"))
    })

    // Underscore codes are not valid BCP 47 tags: passed raw to toLocaleString they
    // throw RangeError, which is what happened for the pt_BR and zh_CN locales.
    it.each(["pt_BR", "zh_CN"])("formats for the underscore locale %s instead of throwing", (lang) => {
        localStorage.setItem("lang", lang)

        expect(humanizeNumber("1234567")).toBe((1234567).toLocaleString(lang.replace("_", "-")))
    })

    it("formats for a plain locale code", () => {
        localStorage.setItem("lang", "de")

        expect(humanizeNumber("1234567")).toBe((1234567).toLocaleString("de"))
    })
})

describe("date", () => {
    const INSTANT = "2026-07-24T13:16:00.000Z"
    const TIMEZONE = "America/Los_Angeles"

    beforeEach(() => localStorage.clear())
    afterEach(() => localStorage.clear())

    it("formats in the timezone from settings rather than the machine one", () => {
        localStorage.setItem(storageKeys.TIMEZONE_STORAGE_KEY, TIMEZONE)

        // 13:16 UTC is 06:16 in Los Angeles, so a wrong timezone shows a different hour.
        expect(date(INSTANT, "HH:mm:ss")).toBe("06:16:00")
    })

    // The Gantt scale divides a time span into tick timestamps, so it has epoch millis rather
    // than a string. Pre-serialising those with toISOString() threw on a non-finite value.
    it.each([
        ["an epoch millisecond timestamp", dayjs(INSTANT).valueOf()],
        ["a Date", new Date(INSTANT)],
        ["an ISO string", INSTANT],
    ])("accepts %s", (_label, value) => {
        localStorage.setItem(storageKeys.TIMEZONE_STORAGE_KEY, TIMEZONE)

        expect(date(value as string | number | Date, "HH:mm:ss")).toBe("06:16:00")
    })

    // An execution cancelled before any task started yields a non-finite span; the label must
    // degrade rather than throw, which is what `new Date(NaN).toISOString()` did.
    it.each([
        ["NaN", NaN],
        ["-Infinity", -Infinity],
    ])("degrades to a placeholder for %s instead of throwing", (_label, value) => {
        expect(() => date(value, "HH:mm:ss")).not.toThrow()
        expect(date(value, "HH:mm:ss")).toBe("Invalid Date")
    })

    it("resolves the \"iso\" sentinel to a full timestamp", () => {
        localStorage.setItem(storageKeys.TIMEZONE_STORAGE_KEY, "UTC")

        expect(date(INSTANT, "iso")).toBe("2026-07-24 13:16:00.000")
    })
})

describe("humanizeDuration", () => {
    // humanDuration reads the unit language from localStorage, so a leftover "lang" from
    // another test would swap "s"/"m"/"h" for their translations and break these assertions.
    afterEach(() => {
        localStorage.removeItem("lang")
    })

    it("formats a sub-second duration rather than rounding it away", () => {
        // The trailing-decimal padding (.5s -> .50s) is the branch a single-digit case exercises.
        expect(humanizeDuration(0.5)).toBe("0.50s")
    })

    it.each([
        ["seconds", 5, "5s"],
        ["minutes", 60, "1m"],
        ["hours", 3600, "1h"],
    ])("renders %s with their own unit", (_label, seconds, expected) => {
        expect(humanizeDuration(seconds)).toBe(expected)
    })

    it("renders the significant units of a duration that spans several", () => {
        // largest is capped at 2, so an hour-and-a-bit shows hours and minutes but drops the seconds.
        expect(humanizeDuration(3661)).toBe("1h, 1m")
        expect(humanizeDuration(90061)).toBe("1d, 1h")
    })

    it("renders zero as a real value rather than an empty string", () => {
        expect(humanizeDuration(0)).toBe("0s")
    })

    // Durations are read off execution records that can be missing, so a nullish value must
    // degrade to a label rather than throw on the hot path every execution row goes through.
    it.each([
        ["undefined", undefined],
        ["null", null],
    ])("does not throw for %s", (_label, value) => {
        expect(() => humanizeDuration(value as unknown as number)).not.toThrow()
        expect(humanizeDuration(value as unknown as number)).toBe("0s")
    })

    it("handles a negative duration rather than producing nonsense", () => {
        // A clock skew between workers can yield a negative span; it must still format to a
        // real, non-empty unit label instead of "NaN" or an empty string.
        const result = humanizeDuration(-5)

        expect(result).not.toBe("")
        expect(result).not.toMatch(/nan/i)
        expect(result).toBe("5s")
    })
})
