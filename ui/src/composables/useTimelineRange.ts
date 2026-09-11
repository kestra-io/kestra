import {computed} from "vue"
import {useRoute, useRouter} from "vue-router"
import {durationUtils} from "@kestra-io/design-system"

export interface TimelineRangePreset {
    value: string;
    labelKey: string;
}

// Same ladder as the generic execution time-range filter (TimeSelect.vue), narrowed to the
// durations that make sense as a timeline zoom level.
export const TIMELINE_RANGE_PRESETS: TimelineRangePreset[] = [
    {value: "PT1H", labelKey: "datepicker.last1hour"},
    {value: "PT12H", labelKey: "datepicker.last12hours"},
    {value: "PT24H", labelKey: "datepicker.last24hours"},
    {value: "PT168H", labelKey: "datepicker.last7days"},
    {value: "PT720H", labelKey: "datepicker.last30days"},
]

const DEFAULT_RANGE_MS = durationUtils.duration("PT24H") * 1000
export const MIN_RANGE_MS = durationUtils.duration("PT5M") * 1000
export const MAX_RANGE_MS = durationUtils.duration("PT8760H") * 1000
const START_QUERY_KEY = "filters[startDate][GREATER_THAN_OR_EQUAL_TO]"
const END_QUERY_KEY = "filters[endDate][LESS_THAN_OR_EQUAL_TO]"
// startDate/endDate and timeRange are mutually exclusive execution filters on the backend
// (a request carrying both is rejected with a 422), so writing one must always drop the other.
const TIME_RANGE_QUERY_KEY = "filters[timeRange][EQUALS]"

// How much wider than the current selection the range-slider's track represents, so the
// selection always renders as a graspable handle instead of a sliver on a fixed year-long track.
const SLIDER_DOMAIN_MULTIPLIER = 8
const SLIDER_DOMAIN_FLOOR_MS = durationUtils.duration("P1D") * 1000
const SLIDER_DOMAIN_CEILING_MS = durationUtils.duration("P120D") * 1000

/**
 * The slider's domain (its full track span), sized relative to the current selection so it stays
 * visually substantial at any zoom level, and recentered on the selection's midpoint.
 */
export function computeSliderDomain(rangeStartMs: number, rangeEndMs: number): [number, number] {
    const span = Math.max(rangeEndMs - rangeStartMs, MIN_RANGE_MS)
    const boundedSpan = Math.min(Math.max(span * SLIDER_DOMAIN_MULTIPLIER, SLIDER_DOMAIN_FLOOR_MS), SLIDER_DOMAIN_CEILING_MS)
    const domainSpan = Math.max(boundedSpan, span)
    const center = (rangeStartMs + rangeEndMs) / 2
    return [center - domainSpan / 2, center + domainSpan / 2]
}

/**
 * Owns the Executions timeline's visible [start, end) window and keeps it in sync with the
 * `startDate`/`endDate` execution filters, so the same window drives the timeline chart, the
 * executions table below it, and the URL together.
 */
export function useTimelineRange() {
    const route = useRoute()
    const router = useRouter()

    const rangeEndMs = computed<number>(() => {
        const raw = route.query[END_QUERY_KEY]
        const parsed = typeof raw === "string" ? Date.parse(raw) : NaN
        return Number.isNaN(parsed) ? Date.now() : parsed
    })

    const rangeStartMs = computed<number>(() => {
        const raw = route.query[START_QUERY_KEY]
        const parsed = typeof raw === "string" ? Date.parse(raw) : NaN
        return Number.isNaN(parsed) ? rangeEndMs.value - DEFAULT_RANGE_MS : parsed
    })

    const activePreset = computed<string | undefined>(() => {
        const span = rangeEndMs.value - rangeStartMs.value
        const isPinnedToNow = Date.now() - rangeEndMs.value < 60_000
        if (!isPinnedToNow) return undefined
        return TIMELINE_RANGE_PRESETS.find(preset => Math.abs(durationUtils.duration(preset.value) * 1000 - span) < 1000)?.value
    })

    function setRange(startMs: number, endMs: number) {
        const clampedSpan = Math.min(Math.max(endMs - startMs, MIN_RANGE_MS), MAX_RANGE_MS)
        const {[TIME_RANGE_QUERY_KEY]: _timeRange, ...rest} = route.query
        router.push({
            query: {
                ...rest,
                [START_QUERY_KEY]: new Date(endMs - clampedSpan).toISOString(),
                [END_QUERY_KEY]: new Date(endMs).toISOString(),
                page: undefined,
            },
        })
    }

    function applyPreset(durationIso: string) {
        const end = Date.now()
        setRange(end - durationUtils.duration(durationIso) * 1000, end)
    }

    function zoom(factor: number) {
        const span = rangeEndMs.value - rangeStartMs.value
        const center = (rangeEndMs.value + rangeStartMs.value) / 2
        const newHalfSpan = (span * factor) / 2
        setRange(center - newHalfSpan, center + newHalfSpan)
    }

    function pan(fractionOfSpan: number) {
        const span = rangeEndMs.value - rangeStartMs.value
        const offset = span * fractionOfSpan
        setRange(rangeStartMs.value + offset, rangeEndMs.value + offset)
    }

    function goToNow() {
        setRange(Date.now() - (rangeEndMs.value - rangeStartMs.value), Date.now())
    }

    return {rangeStartMs, rangeEndMs, activePreset, setRange, applyPreset, zoom, pan, goToNow}
}
