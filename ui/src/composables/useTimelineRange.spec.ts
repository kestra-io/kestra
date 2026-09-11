import {afterEach, describe, expect, it} from "vitest"
import {defineComponent, h} from "vue"
import {flushPromises, mount, VueWrapper} from "@vue/test-utils"
import {createRouter, createMemoryHistory, type Router} from "vue-router"
import {computeSliderDomain, useTimelineRange} from "./useTimelineRange"

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

describe("computeSliderDomain", () => {
    it("sizes the domain to a multiple of the selection so it stays a graspable handle", () => {
        const dayMs = 24 * 60 * 60 * 1000
        const rangeEndMs = Date.now()
        const rangeStartMs = rangeEndMs - dayMs

        const [domainStartMs, domainEndMs] = computeSliderDomain(rangeStartMs, rangeEndMs)

        const domainSpan = domainEndMs - domainStartMs
        const selectionSpan = rangeEndMs - rangeStartMs
        expect(domainSpan).toBe(selectionSpan * 8)
        // The selection should occupy a substantial, graspable share of the track (1/8th here).
        expect(selectionSpan / domainSpan).toBeGreaterThan(0.1)
    })

    it("floors the domain to a day-scale span for a very narrow selection", () => {
        const rangeEndMs = Date.now()
        const rangeStartMs = rangeEndMs - 5 * 60 * 1000

        const [domainStartMs, domainEndMs] = computeSliderDomain(rangeStartMs, rangeEndMs)

        expect(domainEndMs - domainStartMs).toBe(24 * 60 * 60 * 1000)
    })

    it("caps the domain instead of ballooning for a wide-but-not-huge selection", () => {
        const rangeEndMs = Date.now()
        const rangeStartMs = rangeEndMs - 60 * 24 * 60 * 60 * 1000

        const [domainStartMs, domainEndMs] = computeSliderDomain(rangeStartMs, rangeEndMs)

        expect(domainEndMs - domainStartMs).toBe(120 * 24 * 60 * 60 * 1000)
    })

    it("widens the domain past the cap when the selection itself is wider than the cap", () => {
        const rangeEndMs = Date.now()
        const rangeStartMs = rangeEndMs - 200 * 24 * 60 * 60 * 1000

        const [domainStartMs, domainEndMs] = computeSliderDomain(rangeStartMs, rangeEndMs)

        expect(domainEndMs - domainStartMs).toBe(rangeEndMs - rangeStartMs)
    })

    it("recenters the domain on the selection's midpoint", () => {
        const rangeStartMs = 1_000_000
        const rangeEndMs = 2_000_000

        const [domainStartMs, domainEndMs] = computeSliderDomain(rangeStartMs, rangeEndMs)

        const domainCenter = (domainStartMs + domainEndMs) / 2
        const selectionCenter = (rangeStartMs + rangeEndMs) / 2
        expect(domainCenter).toBe(selectionCenter)
    })
})
