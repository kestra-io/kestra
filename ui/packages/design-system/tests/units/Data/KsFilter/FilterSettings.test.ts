import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {computed, defineComponent, h, provide} from "vue"
import {createMemoryHistory, createRouter} from "vue-router"
import FilterSettings from "../../../../src/components/Data/KsDataTable/filter/segments/FilterSettings.vue"
import {FILTER_CONTEXT_INJECTION_KEY} from "../../../../src/components/Data/KsDataTable/filter/utils/filterInjectionKeys"
import type {FilterContext} from "../../../../src/index"
import {i18nMount} from "../../i18nMount"

const ROUTE_NAME = "periodic-refresh-test"

const router = createRouter({
    history: createMemoryHistory(),
    routes: [{path: "/", name: ROUTE_NAME, component: {template: "<div/>"}}],
})

describe("FilterSettings periodic refresh", () => {
    beforeEach(async () => {
        vi.useFakeTimers()
        localStorage.setItem("autoRefreshInterval", "1")
        localStorage.setItem(`autoRefreshEnabled_${ROUTE_NAME}`, "true")
        await router.push("/")
        await router.isReady()
    })

    afterEach(() => {
        vi.useRealTimers()
        localStorage.removeItem("autoRefreshInterval")
        localStorage.removeItem(`autoRefreshEnabled_${ROUTE_NAME}`)
    })

    test("fires the refresh callback once per periodic tick", () => {
        const refreshCallback = vi.fn()

        const Harness = defineComponent({
            setup() {
                provide(FILTER_CONTEXT_INJECTION_KEY, {
                    configuration: computed(() => ({title: "", keys: []})),
                    tableOptions: computed(() => ({chart: {shown: true}, refresh: {shown: true, callback: refreshCallback}})),
                    chartVisible: computed(() => true),
                    updateChart: vi.fn(),
                    refreshData: refreshCallback,
                } as unknown as FilterContext)
                return () => h(FilterSettings)
            },
        })

        const wrapper = i18nMount(Harness, {
            global: {
                plugins: [router],
                stubs: {
                    "ks-button": true,
                    "ks-tooltip": true,
                    "ks-switch": true,
                },
            },
        })

        // the periodic-refresh switch is on from localStorage, so the interval is
        // already running on mount; one tick must reach the page's refresh once,
        // like the manual refresh button, instead of firing the callback twice
        vi.advanceTimersByTime(1000)
        expect(refreshCallback).toHaveBeenCalledTimes(1)

        vi.advanceTimersByTime(1000)
        expect(refreshCallback).toHaveBeenCalledTimes(2)

        wrapper.unmount()
    })
})
