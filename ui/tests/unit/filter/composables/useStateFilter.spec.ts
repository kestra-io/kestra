import {describe, it, expect, vi, beforeEach} from "vitest"

const {route, push} = vi.hoisted(() => ({
    route: {query: {} as Record<string, any>, params: {} as Record<string, any>},
    push: vi.fn(),
}))

vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({push}),
}))

import {executionsListFilteredByState, useStateFilter} from "../../../../src/components/filter/composables/useStateFilter"

describe("executionsListFilteredByState", () => {
    it("builds the executions list location with the state filter", () => {
        expect(executionsListFilteredByState("RUNNING", "main")).toEqual({
            name: "executions/list",
            params: {tenant: "main"},
            query: {"filters[state][IN]": "RUNNING"},
        })
    })

    it("merges extra scope and omits tenant when absent", () => {
        expect(executionsListFilteredByState("FAILED", undefined, {"filters[flowId][EQUALS]": "f"})).toEqual({
            name: "executions/list",
            params: {},
            query: {"filters[state][IN]": "FAILED", "filters[flowId][EQUALS]": "f"},
        })
    })
})

describe("useStateFilter", () => {
    beforeEach(() => {
        push.mockClear()
        route.query = {}
        route.params = {}
    })

    it("filterByState drops legacy state + page, resets to page 1, keeps other query keys", () => {
        route.query = {state: "OLD", page: "4", sort: "x", "filters[namespace][IN]": "io"}
        useStateFilter().filterByState("FAILED")
        expect(push).toHaveBeenCalledWith({
            query: {
                sort: "x",
                "filters[namespace][IN]": "io",
                "filters[state][IN]": "FAILED",
                page: "1",
            },
        })
    })

    it("filterByState toggles off when re-clicking the only active state, dropping the param", () => {
        route.query = {"filters[state][IN]": "SUCCESS", sort: "x", page: "3"}
        useStateFilter().filterByState("SUCCESS")
        expect(push).toHaveBeenCalledWith({query: {sort: "x", page: "1"}})
    })

    it("filterByState toggles off when the only active state is a single-element array", () => {
        route.query = {"filters[state][IN]": ["SUCCESS"]}
        useStateFilter().filterByState("SUCCESS")
        expect(push).toHaveBeenCalledWith({query: {page: "1"}})
    })

    it("filterByState replaces when a different state is active", () => {
        route.query = {"filters[state][IN]": "SUCCESS"}
        useStateFilter().filterByState("FAILED")
        expect(push).toHaveBeenCalledWith({query: {"filters[state][IN]": "FAILED", page: "1"}})
    })

    it("filterByState no-ops without a state", () => {
        useStateFilter().filterByState(undefined)
        expect(push).not.toHaveBeenCalled()
    })

    it("navigateToStateFilter routes to the executions list with tenant + state", () => {
        route.params = {tenant: "main"}
        useStateFilter().navigateToStateFilter("SUCCESS")
        expect(push).toHaveBeenCalledWith({
            name: "executions/list",
            params: {tenant: "main"},
            query: {"filters[state][IN]": "SUCCESS"},
        })
    })

    it("navigateToStateFilter no-ops without a state", () => {
        useStateFilter().navigateToStateFilter()
        expect(push).not.toHaveBeenCalled()
    })
})
