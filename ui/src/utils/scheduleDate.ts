import moment from "moment-timezone"

export function isPastScheduleDate(value: string | undefined | null, now: Date = new Date()): boolean {
    if (!value) {
        return false
    }

    const parsed = moment(value)

    return parsed.isValid() && parsed.valueOf() < now.getTime()
}

export function isScheduleDayDisabled(day: Date, now: Date = new Date()): boolean {
    return moment(day).startOf("day").isBefore(moment(now).startOf("day"))
}

export function buildScheduleDateParam(value: string | undefined | null, timezone: string): string | undefined {
    if (!value) {
        return undefined
    }

    return moment(value).tz(timezone).toISOString(true)
}
