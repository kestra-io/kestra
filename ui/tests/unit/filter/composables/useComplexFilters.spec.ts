import {describe, it, expect, vi, beforeEach} from "vitest"

const {route} = vi.hoisted(() => ({
    route: {query: {} as Record<string, any>},
}))

vi.mock("vue-router", () => ({
    useRoute: () => route,
}))

import {useComplexFilters} from "../../../../src/components/filter/composables/useComplexFilters"

const set = (query: Record<string, any>) => {
    route.query = query
}

describe("useComplexFilters", () => {
    beforeEach(() => {
        route.query = {}
    })

    it("is not complex with no filters", () => {
        expect(useComplexFilters().hasComplexFilters.value).toBe(false)
    })

    it("is not complex with a single non-global filter", () => {
        set({"filters[state][IN]": "RUNNING"})
        expect(useComplexFilters().hasComplexFilters.value).toBe(false)
    })

    it("does not count the time range toward complexity", () => {
        set({"filters[timeRange][EQUALS]": "PT24H", "filters[state][IN]": "RUNNING"})
        expect(useComplexFilters().hasComplexFilters.value).toBe(false)
    })

    it("does not count the search query toward complexity", () => {
        set({"filters[q][EQUALS]": "hello", "filters[state][IN]": "RUNNING"})
        expect(useComplexFilters().hasComplexFilters.value).toBe(false)
    })

    it("is complex with two distinct non-global fields", () => {
        set({"filters[state][IN]": "RUNNING", "filters[namespace][IN]": "io.kestra"})
        expect(useComplexFilters().hasComplexFilters.value).toBe(true)
    })

    it("is complex when any param is grouped", () => {
        set({"filters[or][0][state][EQUALS]": "RUNNING"})
        expect(useComplexFilters().hasComplexFilters.value).toBe(true)
    })
})
