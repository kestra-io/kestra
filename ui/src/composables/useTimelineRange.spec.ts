import {afterEach, describe, expect, it} from "vitest"
import {defineComponent, h} from "vue"
import {flushPromises, mount, VueWrapper} from "@vue/test-utils"
import {createRouter, createMemoryHistory, type Router} from "vue-router"
import {useTimelineRange} from "../../../src/composables/useTimelineRange"

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
