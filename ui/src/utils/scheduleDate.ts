/**
 * Helpers for the Execute dialog "Schedule date" picker.
 * Past dates/times must be blocked — the executor ignores past scheduleDate
 * and starts immediately, which makes a past selection misleading.
 */

const MS_PER_DAY = 8.64e7

/** Calendar days strictly before today (local) are disabled. */
export function isScheduleCalendarDateDisabled(date: Date, now: Date = new Date()): boolean {
    return date.getTime() < now.getTime() - MS_PER_DAY
}

function isSameLocalDay(a: Date, b: Date): boolean {
    return (
        a.getFullYear() === b.getFullYear() &&
        a.getMonth() === b.getMonth() &&
        a.getDate() === b.getDate()
    )
}

/** Hours already past on the selected day (local). Empty when the day is in the future. */
export function disabledScheduleHours(selected: Date | string | undefined | null, now: Date = new Date()): number[] {
    if (selected == null || selected === "") {
        return []
    }
    const selectedDate = selected instanceof Date ? selected : new Date(selected)
    if (Number.isNaN(selectedDate.getTime()) || !isSameLocalDay(selectedDate, now)) {
        return []
    }
    const currentHour = now.getHours()
    return Array.from({length: currentHour}, (_, hour) => hour)
}

/** Minutes already past for the selected hour on today (local). */
export function disabledScheduleMinutes(
    selected: Date | string | undefined | null,
    hour: number,
    now: Date = new Date(),
): number[] {
    if (selected == null || selected === "") {
        return []
    }
    const selectedDate = selected instanceof Date ? selected : new Date(selected)
    if (Number.isNaN(selectedDate.getTime()) || !isSameLocalDay(selectedDate, now)) {
        return []
    }
    if (hour !== now.getHours()) {
        return []
    }
    const currentMinute = now.getMinutes()
    return Array.from({length: currentMinute}, (_, minute) => minute)
}

/** True when the schedule instant is strictly before now. */
export function isScheduleDateInPast(
    scheduleDate: Date | string | undefined | null,
    now: Date = new Date(),
): boolean {
    if (scheduleDate == null || scheduleDate === "") {
        return false
    }
    const date = scheduleDate instanceof Date ? scheduleDate : new Date(scheduleDate)
    if (Number.isNaN(date.getTime())) {
        return false
    }
    return date.getTime() < now.getTime()
}
