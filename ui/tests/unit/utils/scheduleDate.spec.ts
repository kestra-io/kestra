import {describe, expect, it} from "vitest"
import {
    buildScheduleDateParam,
    isPastScheduleDate,
    isScheduleDayDisabled,
} from "../../../src/utils/scheduleDate"

const NOW = new Date("2026-09-03T12:00:00.000Z")

describe("scheduleDate", () => {
    it("shouldRejectAPastDateTime", () => {
        expect(isPastScheduleDate("2026-09-03T11:59:00.000Z", NOW)).toBe(true)
        expect(isPastScheduleDate("2026-09-01T10:00:00.000Z", NOW)).toBe(true)
    })

    it("shouldAcceptAFutureDateTime", () => {
        expect(isPastScheduleDate("2026-09-03T12:01:00.000Z", NOW)).toBe(false)
        expect(isPastScheduleDate("2026-12-01T00:00:00.000Z", NOW)).toBe(false)
    })

    it("shouldAcceptTheAbsenceOfASchedule", () => {
        expect(isPastScheduleDate(undefined, NOW)).toBe(false)
        expect(isPastScheduleDate("", NOW)).toBe(false)
    })

    it("shouldNotRejectAnUnparsableValue", () => {
        expect(isPastScheduleDate("not a date", NOW)).toBe(false)
    })

    it("shouldKeepTodaySelectableWhileDisablingEarlierDays", () => {
        const localNow = new Date(2026, 8, 3, 12, 0, 0)
        expect(isScheduleDayDisabled(new Date(2026, 8, 3, 0, 0, 0), localNow)).toBe(false)
        expect(isScheduleDayDisabled(new Date(2026, 8, 3, 23, 30, 0), localNow)).toBe(false)
        expect(isScheduleDayDisabled(new Date(2026, 8, 2, 23, 30, 0), localNow)).toBe(true)
        expect(isScheduleDayDisabled(new Date(2026, 8, 4, 0, 0, 0), localNow)).toBe(false)
    })

    it("shouldOmitTheParamWhenNoDateIsSet", () => {
        expect(buildScheduleDateParam(undefined, "UTC")).toBeUndefined()
        expect(buildScheduleDateParam("", "UTC")).toBeUndefined()
    })

    it("shouldBuildATimezoneAwareIsoStringWhenSet", () => {
        expect(buildScheduleDateParam("2026-09-03T12:00:00.000Z", "Europe/Paris"))
            .toBe("2026-09-03T14:00:00.000+02:00")
        expect(buildScheduleDateParam("2026-09-03T12:00:00.000Z", "UTC"))
            .toBe("2026-09-03T12:00:00.000+00:00")
    })
})
