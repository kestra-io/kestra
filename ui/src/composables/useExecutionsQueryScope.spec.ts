import {afterEach, describe, expect, it} from "vitest"
import {computed, defineComponent, h} from "vue"
import {mount, VueWrapper} from "@vue/test-utils"
import {createRouter, createMemoryHistory, type Router} from "vue-router"
import type {FilterConfiguration} from "@kestra-io/design-system"
import {useExecutionsQueryScope} from "./useExecutionsQueryScope"

const configuration = {
    title: "Executions",
    searchPlaceholder: "Search",
    keys: [{key: "namespace"}, {key: "flowId"}, {key: "state"}, {key: "timeRange"}],
} as unknown as FilterConfiguration

function createTestRouter(): Router {
    return createRouter({
        history: createMemoryHistory(),
        routes: [{name: "executions/list", path: "/:tenant?/executions", component: {template: "<div/>"}}],
    })
}

async function mountScope(router: Router, query: Record<string, string>, scope: Record<string, unknown> = {}) {
    await router.push({name: "executions/list", params: {tenant: "main"}, query})
    await router.isReady()

    let api!: ReturnType<typeof useExecutionsQueryScope>
    const wrapper = mount(defineComponent({
        setup() {
            api = useExecutionsQueryScope(computed(() => configuration), computed(() => scope))
            return () => h("div")
        },
    }), {global: {plugins: [router]}})

    return {wrapper, api}
}

describe("useExecutionsQueryScope", () => {
    let wrapper: VueWrapper

    afterEach(() => {
        wrapper?.unmount()
    })

    it("should treat startDate and endDate as supported whenever timeRange is, so a date window survives scoping", async () => {
        const mounted = await mountScope(createTestRouter(), {})
        wrapper = mounted.wrapper

        const supported = mounted.api.supportedFilterFields.value

        expect(supported.has("timeRange")).toBe(true)
        expect(supported.has("startDate")).toBe(true)
        expect(supported.has("endDate")).toBe(true)
    })

    it("should drop a filter the configuration does not declare", async () => {
        const mounted = await mountScope(createTestRouter(), {})
        wrapper = mounted.wrapper

        const kept = mounted.api.dropUnsupportedFilters({
            "filters[namespace][PREFIX]": "company",
            "filters[labels][EQUALS]": "env:prod",
        })

        expect(Object.keys(kept)).toEqual(["filters[namespace][PREFIX]"])
    })

    it("should leave pagination and sorting out of the query it builds", async () => {
        const mounted = await mountScope(createTestRouter(), {
            page: "3",
            size: "50",
            sort: "state.startDate:desc",
            "filters[namespace][PREFIX]": "company",
        })
        wrapper = mounted.wrapper

        const query = mounted.api.loadQuery({size: 1000})

        expect(query.page).toBeUndefined()
        expect(query.sort).toBeUndefined()
        expect(query.size).toBe(1000)
        expect(query["filters[namespace][PREFIX]"]).toBe("company")
    })

    it("should keep an explicit state filter from the URL instead of overwriting it with the scope's statuses", async () => {
        const mounted = await mountScope(
            createTestRouter(),
            {"filters[state][NOT_IN]": "FAILED"},
            {statuses: ["SUCCESS"]},
        )
        wrapper = mounted.wrapper

        const query = mounted.api.loadQuery({})

        expect(query["filters[state][NOT_IN]"]).toBe("FAILED")
        expect(query["filters[state][IN]"]).toBeUndefined()
    })

    it("should apply the scope's statuses when the URL carries no state filter", async () => {
        const mounted = await mountScope(createTestRouter(), {}, {statuses: ["SUCCESS", "WARNING"]})
        wrapper = mounted.wrapper

        expect(mounted.api.loadQuery({})["filters[state][IN]"]).toBe("SUCCESS,WARNING")
    })
})
