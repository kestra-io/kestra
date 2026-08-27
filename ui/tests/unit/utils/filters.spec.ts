import {afterEach, beforeEach, describe, expect, it} from "vitest"
import moment from "moment-timezone"
import {date, humanizeNumber} from "../../../src/utils/filters"
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
        ["an epoch millisecond timestamp", moment(INSTANT).valueOf()],
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
        expect(date(value, "HH:mm:ss")).toBe("Invalid date")
    })

    it("resolves the \"iso\" sentinel to a full timestamp", () => {
        localStorage.setItem(storageKeys.TIMEZONE_STORAGE_KEY, "UTC")

        expect(date(INSTANT, "iso")).toBe("2026-07-24 13:16:00.000")
    })
})
