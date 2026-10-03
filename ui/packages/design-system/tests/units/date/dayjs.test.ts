import {describe, test, expect} from "vitest"
import dayjs from "../../../src/date/dayjs"

describe("dayjs plugin registration", () => {
    test("registers utc and timezone plugins with dayjs.utc and dayjs.tz", () => {
        expect(typeof dayjs.utc).toBe("function")
        expect(typeof dayjs.tz).toBe("function")

        const utcDate = dayjs.utc("2026-01-01T12:00:00Z")
        expect(utcDate.isValid()).toBe(true)
        expect(utcDate.isUTC()).toBe(true)

        const tzDate = dayjs("2026-01-01T12:00:00Z").tz("America/New_York")
        expect(tzDate.isValid()).toBe(true)
    })

    test("registers duration plugin with dayjs.duration and formats duration", () => {
        expect(typeof dayjs.duration).toBe("function")

        const dur = dayjs.duration(120, "seconds")
        expect(dur.asMinutes()).toBe(2)
        expect(dur.humanize()).toBe("2 minutes")
    })

    test("makes calendar, isoWeek, week, isSameOrBefore, fromNow, and format('LT') available on dayjs instances", () => {
        const date = dayjs("2026-01-01T12:00:00Z")

        expect(typeof date.calendar).toBe("function")
        expect(typeof date.isoWeek).toBe("function")
        expect(typeof date.week).toBe("function")
        expect(typeof date.isSameOrBefore).toBe("function")
        expect(typeof date.fromNow).toBe("function")

        expect(typeof date.calendar()).toBe("string")
        expect(typeof date.isoWeek()).toBe("number")
        expect(typeof date.week()).toBe("number")
        expect(date.isSameOrBefore(dayjs("2026-01-02"))).toBe(true)
        expect(typeof date.fromNow()).toBe("string")

        const localizedTime = date.format("LT")
        expect(typeof localizedTime).toBe("string")
        expect(localizedTime).not.toBe("LT")
    })

    test("registers minMax plugin with dayjs.min and dayjs.max", () => {
        expect(typeof dayjs.min).toBe("function")
        expect(typeof dayjs.max).toBe("function")

        const d1 = dayjs("2026-01-01")
        const d2 = dayjs("2026-01-05")

        expect(dayjs.min(d1, d2)).toEqual(d1)
        expect(dayjs.max(d1, d2)).toEqual(d2)
    })

    test("renders advanced format tokens such as Do rather than echoing them literally", () => {
        const d = dayjs("2026-01-01")
        expect(d.format("Do")).toBe("1st")
        expect(d.format("Do")).not.toBe("Do")
    })

    test("exports the extended instance as the default export", () => {
        expect(dayjs).toBeDefined()
        expect(typeof dayjs).toBe("function")
        expect(typeof dayjs.duration).toBe("function")
        expect(typeof dayjs.utc).toBe("function")
        expect(typeof dayjs.tz).toBe("function")
    })
})
