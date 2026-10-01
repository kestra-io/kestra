import {dateUtils, dayjs} from "@kestra-io/design-system"

export function isPastScheduleDate(value: string | undefined | null, now: Date = new Date()): boolean {
    if (!value) {
        return false
    }

    const parsed = dayjs(value)

    return parsed.isValid() && parsed.valueOf() < now.getTime()
}

export function isScheduleDayDisabled(day: Date, now: Date = new Date()): boolean {
    return dayjs(day).startOf("day").isBefore(dayjs(now).startOf("day"))
}

export function buildScheduleDateParam(value: string | undefined | null, timezone: string): string | undefined {
    if (!value) {
        return undefined
    }

    return dateUtils.toIsoKeepOffset(dayjs(value).tz(timezone))
}
