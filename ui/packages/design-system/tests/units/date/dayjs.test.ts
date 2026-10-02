import {describe, test, expect} from "vitest"
import dayjs from "../../../src/date/dayjs"

describe("dayjs plugin registration", () => {
    test("registers the utc and timezone plugins", () => {
        expect(dayjs.utc).toBeTypeOf("function")
        expect(dayjs.tz).toBeTypeOf("function")
    })

    test("registers the duration plugin and formats a duration", () => {
        expect(dayjs.duration).toBeTypeOf("function")
        expect(dayjs.duration(1, "hour").humanize()).toBe("an hour")
    })

    test("registers the calendar, isoWeek, weekOfYear, isSameOrBefore and relativeTime plugins", () => {
        const now = dayjs()
        expect(typeof now.calendar).toBe("function")
        expect(typeof now.isoWeek).toBe("function")
        expect(typeof now.week).toBe("function")
        expect(typeof now.isSameOrBefore).toBe("function")
        expect(typeof now.fromNow).toBe("function")

        expect(now.isoWeek()).toBeGreaterThan(0)
        expect(now.week()).toBeGreaterThan(0)
        expect(now.isSameOrBefore(now)).toBe(true)
        expect(now.fromNow()).toBe("a few seconds ago")
    })

    test("registers the localizedFormat plugin so the LT token renders a time", () => {
        expect(dayjs().format("LT")).toMatch(/\d/)
    })

    test("registers the minMax plugin and picks the extreme value", () => {
        expect(dayjs.min).toBeTypeOf("function")
        expect(dayjs.max).toBeTypeOf("function")

        const earlier = dayjs("2026-01-02")
        const later = dayjs("2026-01-03")
        expect(dayjs.min(earlier, later).format("YYYY-MM-DD")).toBe("2026-01-02")
        expect(dayjs.max(earlier, later).format("YYYY-MM-DD")).toBe("2026-01-03")
    })

    test("renders advancedFormat tokens instead of echoing them", () => {
        expect(dayjs("2026-01-02").format("Do")).toBe("2nd")
    })

    test("the default export is the extended instance, not a bare dayjs", () => {
        expect(dayjs.utc).toBeDefined()
        expect(dayjs.tz).toBeDefined()
        expect(dayjs.duration).toBeDefined()
        expect(dayjs.min).toBeDefined()
    })
})
