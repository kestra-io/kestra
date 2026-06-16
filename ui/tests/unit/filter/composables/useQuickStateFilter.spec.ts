import {describe, it, expect, vi, beforeEach} from "vitest"

const {route, replace} = vi.hoisted(() => ({
    route: {query: {} as Record<string, any>},
    replace: vi.fn(),
}))

vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({replace}),
}))

import {useQuickStateFilter} from "../../../../src/components/filter/composables/useQuickStateFilter"

const KEY = "filters[state][IN]"

describe("useQuickStateFilter", () => {
    beforeEach(() => {
        replace.mockClear()
        route.query = {}
    })

    it("adds a state to the set when none is selected", () => {
        useQuickStateFilter().onQuickFilterState("FAILED")
        expect(replace).toHaveBeenCalledWith({query: {[KEY]: "FAILED"}})
    })

    it("removes the param entirely when toggling the last selected state off", () => {
        route.query = {[KEY]: "FAILED", sort: "x"}
        useQuickStateFilter().onQuickFilterState("FAILED")
        expect(replace).toHaveBeenCalledWith({query: {sort: "x"}})
    })

    it("removes one state while keeping the others", () => {
        route.query = {[KEY]: "FAILED,RUNNING"}
        useQuickStateFilter().onQuickFilterState("FAILED")
        expect(replace).toHaveBeenCalledWith({query: {[KEY]: "RUNNING"}})
    })

    it("appends a state to an existing selection", () => {
        route.query = {[KEY]: "FAILED"}
        useQuickStateFilter().onQuickFilterState("RUNNING")
        expect(replace).toHaveBeenCalledWith({query: {[KEY]: "FAILED,RUNNING"}})
    })

    it("parses selectedStates from a comma string", () => {
        route.query = {[KEY]: "FAILED, RUNNING"}
        expect(useQuickStateFilter().selectedStates.value).toEqual(["FAILED", "RUNNING"])
    })

    it("parses selectedStates from a repeated-key array", () => {
        route.query = {[KEY]: ["FAILED", "RUNNING"]}
        expect(useQuickStateFilter().selectedStates.value).toEqual(["FAILED", "RUNNING"])
    })

    it("returns no selected states when the param is absent", () => {
        expect(useQuickStateFilter().selectedStates.value).toEqual([])
    })
})
