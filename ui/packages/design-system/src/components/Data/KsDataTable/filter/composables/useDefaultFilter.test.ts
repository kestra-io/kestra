import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {mount, type VueWrapper} from "@vue/test-utils"
import {createMemoryHistory, createRouter, type LocationQuery} from "vue-router"
import {applyDefaultFilters, useDefaultFilter} from "./useDefaultFilter"

let wrapper: VueWrapper | undefined

beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
})

afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    localStorage.clear()
    sessionStorage.clear()
})

describe("applyDefaultFilters", () => {
    test("applies defaults when no filters are set", () => {
        localStorage.setItem("defaultNamespace", "company.team")

        expect(applyDefaultFilters({}, {includeScope: true, includeTimeRange: true})).toEqual({
            query: {
                "filters[namespace][PREFIX]": "company.team",
                "filters[scope][EQUALS]": "USER",
                "filters[timeRange][EQUALS]": "PT24H",
            },
            change: true,
        })
    })

    test("preserves existing filters instead of adding defaults for the same fields", () => {
        localStorage.setItem("defaultNamespace", "company.default")
        const query = {
            "filters[namespace][EQUALS]": "company.selected",
            "filters[scope][EQUALS]": "SYSTEM",
            "filters[timeRange][EQUALS]": "P30D",
        }

        expect(applyDefaultFilters(query, {includeScope: true, includeTimeRange: true})).toEqual({
            query,
            change: false,
        })
    })

    test("adds defaults for missing fields alongside an existing filter", () => {
        localStorage.setItem("defaultNamespace", "company.default")

        expect(applyDefaultFilters({"filters[namespace][EQUALS]": "company.selected"}, {
            includeScope: true,
            includeTimeRange: true,
            defaultDuration: "P7D",
        })).toEqual({
            query: {
                "filters[namespace][EQUALS]": "company.selected",
                "filters[scope][EQUALS]": "USER",
                "filters[timeRange][EQUALS]": "P7D",
            },
            change: true,
        })
    })

    test("leaves filters untouched when no defaults are configured", () => {
        const query = {"filters[namespace][EQUALS]": "company.selected", q: "flow"}

        expect(applyDefaultFilters(query, {})).toEqual({query, change: false})
    })

    test("returns a new query without mutating the original", () => {
        const query = Object.freeze({"filters[namespace][EQUALS]": "company.selected"})
        const result = applyDefaultFilters(query, {includeTimeRange: true})

        expect(result.query).not.toBe(query)
        expect(result.query).toEqual({
            "filters[namespace][EQUALS]": "company.selected",
            "filters[timeRange][EQUALS]": "PT24H",
        })
        expect(query).toEqual({"filters[namespace][EQUALS]": "company.selected"})
    })
})

describe("useDefaultFilter", () => {
    async function mountWithQuery(query: LocationQuery = {}) {
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [{path: "/:tenant/executions", name: "executions", component: {render: () => null}}],
        })
        await router.push({name: "executions", params: {tenant: "main"}, query})
        await router.isReady()
        wrapper = mount({
            setup() {
                useDefaultFilter({includeScope: true, includeTimeRange: true})
                return () => null
            },
        }, {global: {plugins: [router]}})
        return router
    }

    test("seeds the route with defaults on first load", async () => {
        localStorage.setItem("defaultNamespace", "company.team")
        const router = await mountWithQuery()

        await vi.waitFor(() => expect(router.currentRoute.value.query).toEqual({
            "filters[namespace][PREFIX]": "company.team",
            "filters[scope][EQUALS]": "USER",
            "filters[timeRange][EQUALS]": "PT24H",
        }))
    })

    test("keeps URL filters ahead of saved filters and fills only missing defaults", async () => {
        localStorage.setItem("defaultNamespace", "company.default")
        sessionStorage.setItem("executions_main_restore_url", JSON.stringify({
            "filters[namespace][EQUALS]": "company.saved",
            "filters[timeRange][EQUALS]": "P30D",
        }))
        const router = await mountWithQuery({"filters[namespace][EQUALS]": "company.url", q: "flow"})

        await vi.waitFor(() => expect(router.currentRoute.value.query).toEqual({
            "filters[namespace][EQUALS]": "company.url",
            "filters[timeRange][EQUALS]": "P30D",
            "filters[scope][EQUALS]": "USER",
            q: "flow",
        }))
    })
})
