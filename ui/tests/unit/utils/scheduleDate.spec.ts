import {describe, expect, test} from "vitest"
import {
    disabledScheduleHours,
    disabledScheduleMinutes,
    isScheduleCalendarDateDisabled,
    isScheduleDateInPast,
} from "../../../src/utils/scheduleDate"

describe("scheduleDate helpers", () => {
    const now = new Date("2026-07-29T14:30:00")

    test("disables calendar days before today", () => {
        expect(isScheduleCalendarDateDisabled(new Date("2026-07-28T12:00:00"), now)).toBe(true)
        expect(isScheduleCalendarDateDisabled(new Date("2026-07-29T00:00:00"), now)).toBe(false)
        expect(isScheduleCalendarDateDisabled(new Date("2026-07-30T12:00:00"), now)).toBe(false)
    })

    test("disables hours already past when the selected day is today", () => {
        expect(disabledScheduleHours(new Date("2026-07-29T16:00:00"), now)).toEqual(
            Array.from({length: 14}, (_, hour) => hour),
        )
        expect(disabledScheduleHours(new Date("2026-07-30T10:00:00"), now)).toEqual([])
        expect(disabledScheduleHours(undefined, now)).toEqual([])
    })

    test("disables minutes already past for the current hour on today", () => {
        expect(disabledScheduleMinutes(new Date("2026-07-29T14:45:00"), 14, now)).toEqual(
            Array.from({length: 30}, (_, minute) => minute),
        )
        expect(disabledScheduleMinutes(new Date("2026-07-29T15:00:00"), 15, now)).toEqual([])
        expect(disabledScheduleMinutes(new Date("2026-07-30T14:00:00"), 14, now)).toEqual([])
    })

    test("detects schedule dates strictly in the past", () => {
        expect(isScheduleDateInPast(new Date("2026-07-29T14:29:59"), now)).toBe(true)
        expect(isScheduleDateInPast(new Date("2026-07-29T14:30:00"), now)).toBe(false)
        expect(isScheduleDateInPast(new Date("2026-07-29T15:00:00"), now)).toBe(false)
        expect(isScheduleDateInPast(undefined, now)).toBe(false)
        expect(isScheduleDateInPast("", now)).toBe(false)
    })
})
