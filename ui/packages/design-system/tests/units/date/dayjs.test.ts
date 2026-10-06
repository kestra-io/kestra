import {describe, expect, test} from "vitest"
import dayjs from "../../../src/date/dayjs"
import bareDayjs from "dayjs"

describe("dayjs plugin registration", () => {
    test("registers the utc and timezone plugins", () => {
        expect(typeof dayjs.utc).toBe("function")
        expect(typeof dayjs.tz).toBe("function")
        expect(dayjs.utc("2026-10-04").format()).toBe("2026-10-04T00:00:00+00:00")
    })

    test("registers the duration plugin", () => {
        expect(typeof dayjs.duration).toBe("function")
        expect(dayjs.duration(90, "seconds").asMinutes()).toBe(1.5)
    })

    test("registers the calendar, isoWeek, weekOfYear, isSameOrBefore, relativeTime and localizedFormat plugins", () => {
        const date = dayjs("2026-10-04")
        expect(typeof date.calendar).toBe("function")
        expect(typeof date.isoWeek).toBe("function")
        expect(typeof date.week).toBe("function")
        expect(date.isSameOrBefore(dayjs("2026-10-05"))).toBe(true)
        expect(typeof date.fromNow).toBe("function")
        expect(date.format("LT")).not.toBe("LT")
    })

    test("registers the minMax plugin", () => {
        expect(typeof dayjs.min).toBe("function")
        expect(typeof dayjs.max).toBe("function")
        expect(dayjs.max(dayjs("2026-10-04"), dayjs("2026-10-05")).isSame(dayjs("2026-10-05"))).toBe(true)
    })

    test("renders advanced format tokens instead of echoing them literally", () => {
        expect(dayjs("2026-10-04").format("Do")).toBe("4th")
    })

    test("the default export is the shared extended instance, not a separate bare copy", () => {
        expect(dayjs).toBe(bareDayjs)
    })
})
