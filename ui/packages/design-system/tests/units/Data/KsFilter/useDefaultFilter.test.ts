import {afterEach, describe, expect, test, vi} from "vitest"
import {mount, type VueWrapper} from "@vue/test-utils"
import {createMemoryHistory, createRouter} from "vue-router"
import {useDefaultFilter} from "../../../../src/components/Data/KsDataTable/filter/composables/useDefaultFilter"

let wrapper: VueWrapper | undefined

afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    localStorage.clear()
    sessionStorage.clear()
})

describe("useDefaultFilter", () => {
    test("keeps a route param holding a reserved character encoded when seeding defaults", async () => {
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [{path: "/:tenant/dashboards/:dashboard", name: "dashboard", component: {render: () => null}}],
        })
        await router.push({name: "dashboard", params: {tenant: "main", dashboard: "ops?x"}})
        await router.isReady()
        wrapper = mount({
            setup() {
                useDefaultFilter({includeScope: true, includeTimeRange: true})
                return () => null
            },
        }, {global: {plugins: [router]}})

        await vi.waitFor(() => expect(router.currentRoute.value.query).toHaveProperty("filters[timeRange][EQUALS]"))
        expect(router.currentRoute.value.params.dashboard).toBe("ops?x")
        expect(router.currentRoute.value.path).toBe("/main/dashboards/ops%3Fx")
    })
})
