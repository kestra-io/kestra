import {afterEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import {flushPromises, mount, VueWrapper} from "@vue/test-utils"
import {createRouter, createMemoryHistory, type Router} from "vue-router"
import {computeScrubberDomain, useTimelineRange} from "./useTimelineRange"

const TIME_RANGE_QUERY_KEY = "filters[timeRange][EQUALS]"
const START_QUERY_KEY = "filters[startDate][GREATER_THAN_OR_EQUAL_TO]"
const END_QUERY_KEY = "filters[endDate][LESS_THAN_OR_EQUAL_TO]"

function createTestRouter(): Router {
    return createRouter({
        history: createMemoryHistory(),
        routes: [
            {name: "executions/list", path: "/:tenant?/executions", component: {template: "<div/>"}},
        ],
    })
}

function mountTimelineRange(router: Router) {
    let range!: ReturnType<typeof useTimelineRange>
    const wrapper = mount(defineComponent({
        setup() {
            range = useTimelineRange()
            return () => h("div")
        },
    }), {global: {plugins: [router]}})
    return {wrapper, range}
}

describe("useTimelineRange", () => {
    let wrapper: VueWrapper

    afterEach(() => {
        wrapper?.unmount()
    })

    it("clears the timeRange filter when zooming applies a startDate/endDate window", async () => {
        const router = createTestRouter()
        await router.push({
            name: "executions/list",
            params: {tenant: "main"},
            query: {[TIME_RANGE_QUERY_KEY]: "PT24H"},
        })

        const mounted = mountTimelineRange(router)
        wrapper = mounted.wrapper

        mounted.range.zoom(0.5)
        await flushPromises()

        const query = router.currentRoute.value.query
        expect(query[TIME_RANGE_QUERY_KEY]).toBeUndefined()
        expect(query[START_QUERY_KEY]).toBeDefined()
        expect(query[END_QUERY_KEY]).toBeDefined()
    })

    it("clears the timeRange filter when applying a preset", async () => {
        const router = createTestRouter()
        await router.push({
            name: "executions/list",
            params: {tenant: "main"},
            query: {[TIME_RANGE_QUERY_KEY]: "PT24H"},
        })

        const mounted = mountTimelineRange(router)
        wrapper = mounted.wrapper

        mounted.range.applyPreset("PT12H")
        await flushPromises()

        const query = router.currentRoute.value.query
        expect(query[TIME_RANGE_QUERY_KEY]).toBeUndefined()
        expect(query[START_QUERY_KEY]).toBeDefined()
        expect(query[END_QUERY_KEY]).toBeDefined()
    })
})

describe("relative time range", () => {
    const HOUR_MS = 60 * 60 * 1000

    it("should take the window span from the filter bar's relative timeRange, ending now", async () => {
        const router = createTestRouter()
        await router.push({name: "executions/list", params: {tenant: "main"}, query: {[TIME_RANGE_QUERY_KEY]: "PT1H"}})
        const before = Date.now()

        const mounted = mountTimelineRange(router)

        expect(mounted.range.rangeEndMs.value - mounted.range.rangeStartMs.value).toBe(HOUR_MS)
        expect(mounted.range.rangeEndMs.value).toBeGreaterThanOrEqual(before)
        mounted.wrapper.unmount()
    })

    it("should prefer an explicit start and end over a leftover timeRange", async () => {
        const router = createTestRouter()
        await router.push({
            name: "executions/list",
            params: {tenant: "main"},
            query: {
                [TIME_RANGE_QUERY_KEY]: "PT1H",
                [START_QUERY_KEY]: "2026-01-01T00:00:00.000Z",
                [END_QUERY_KEY]: "2026-01-03T00:00:00.000Z",
            },
        })

        const mounted = mountTimelineRange(router)

        expect(mounted.range.rangeEndMs.value - mounted.range.rangeStartMs.value).toBe(48 * HOUR_MS)
        mounted.wrapper.unmount()
    })

    it("should fall back to the default day when the timeRange is not a duration", async () => {
        const router = createTestRouter()
        await router.push({name: "executions/list", params: {tenant: "main"}, query: {[TIME_RANGE_QUERY_KEY]: "garbage"}})

        const mounted = mountTimelineRange(router)

        expect(mounted.range.rangeEndMs.value - mounted.range.rangeStartMs.value).toBe(24 * HOUR_MS)
        mounted.wrapper.unmount()
    })

    it("should move a relative window's end to the current time on refresh", async () => {
        const router = createTestRouter()
        await router.push({name: "executions/list", params: {tenant: "main"}, query: {[TIME_RANGE_QUERY_KEY]: "PT1H"}})
        vi.useFakeTimers({now: Date.parse("2026-10-19T10:00:00.000Z")})
        const mounted = mountTimelineRange(router)
        const firstEndMs = mounted.range.rangeEndMs.value

        vi.setSystemTime(Date.parse("2026-10-19T10:05:00.000Z"))
        mounted.range.refreshNow()

        expect(mounted.range.rangeEndMs.value - firstEndMs).toBe(5 * 60 * 1000)
        vi.useRealTimers()
        mounted.wrapper.unmount()
    })
})

describe("setRange", () => {
    it("should refuse to move the window past now, whichever control asks for it", async () => {
        const router = createTestRouter()
        await router.push({name: "executions/list", params: {tenant: "main"}, query: {}})
        await router.isReady()

        const mounted = mountTimelineRange(router)
        const nowMs = Date.now()

        mounted.range.setRange(nowMs, nowMs + 24 * 60 * 60 * 1000)
        await flushPromises()

        const endMs = Date.parse(router.currentRoute.value.query[END_QUERY_KEY] as string)
        expect(endMs).toBeLessThanOrEqual(Date.now())

        mounted.wrapper.unmount()
    })
})

describe("computeScrubberDomain", () => {
    const DAY_MS = 24 * 60 * 60 * 1000

    it("should span twice the window so the selection stays a graspable half of the track", () => {
        const nowMs = Date.parse("2026-09-21T12:00:00.000Z")
        const rangeEndMs = nowMs - DAY_MS
        const rangeStartMs = rangeEndMs - DAY_MS

        const [domainStartMs, domainEndMs] = computeScrubberDomain(rangeStartMs, rangeEndMs, nowMs)

        expect(domainEndMs - domainStartMs).toBe(2 * DAY_MS)
        expect(domainStartMs).toBeLessThanOrEqual(rangeStartMs)
        expect(domainEndMs).toBeGreaterThanOrEqual(rangeEndMs)
    })

    it("should stop at now rather than offering future track no execution can land in", () => {
        const nowMs = Date.parse("2026-09-21T12:00:00.000Z")

        const [domainStartMs, domainEndMs] = computeScrubberDomain(nowMs - DAY_MS, nowMs, nowMs)

        expect(domainEndMs).toBe(nowMs)
        expect(domainStartMs).toBe(nowMs - 2 * DAY_MS)
    })

    it("should still contain a window that already ends in the future", () => {
        const nowMs = Date.parse("2026-09-21T12:00:00.000Z")
        const rangeEndMs = nowMs + DAY_MS

        const [, domainEndMs] = computeScrubberDomain(nowMs, rangeEndMs, nowMs)

        expect(domainEndMs).toBeGreaterThanOrEqual(rangeEndMs)
    })
})
