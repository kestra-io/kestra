import dayjs from "dayjs"
import advancedFormat from "dayjs/plugin/advancedFormat"
import calendar from "dayjs/plugin/calendar"
import duration from "dayjs/plugin/duration"
import isSameOrBefore from "dayjs/plugin/isSameOrBefore"
import isoWeek from "dayjs/plugin/isoWeek"
import localizedFormat from "dayjs/plugin/localizedFormat"
import minMax from "dayjs/plugin/minMax"
import relativeTime from "dayjs/plugin/relativeTime"
import timezone from "dayjs/plugin/timezone"
import weekOfYear from "dayjs/plugin/weekOfYear"
import utc from "dayjs/plugin/utc"

// The single configured dayjs instance for the whole product: plugins are registered globally, so
// a second `import dayjs from "dayjs"` anywhere else would silently lack them.
dayjs.extend(utc)
dayjs.extend(timezone)
dayjs.extend(duration)
dayjs.extend(advancedFormat)
dayjs.extend(calendar)
dayjs.extend(isSameOrBefore)
dayjs.extend(isoWeek)
dayjs.extend(localizedFormat)
dayjs.extend(minMax)
dayjs.extend(relativeTime)
dayjs.extend(weekOfYear)

export default dayjs
export type {Dayjs} from "dayjs"

// Declaration emit drops the plugin imports above, so a consumer reading our built .d.ts gets a
// dayjs without them; add every plugin with a `declare module "dayjs"` block or only ui-ee fails.
export type DayjsPlugins = [
    typeof calendar,
    typeof duration,
    typeof isSameOrBefore,
    typeof isoWeek,
    typeof minMax,
    typeof relativeTime,
    typeof timezone,
    typeof utc,
    typeof weekOfYear,
]
