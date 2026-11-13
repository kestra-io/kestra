import {describe, it, expect, vi, beforeEach} from "vitest"
import type {LocationQuery} from "vue-router"

const replace = vi.fn()
let routeQuery: LocationQuery = {}

vi.mock("vue-router", () => ({
    useRoute: () => ({query: routeQuery}),
    useRouter: () => ({replace}),
}))

import {useQueryChoice, usePluginView} from "./pluginView"

describe("useQueryChoice", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        routeQuery = {}
    })

    it("falls back to the first choice when the query parameter is absent", () => {
        const choice = useQueryChoice("layout", ["grid", "list"] as const)
        expect(choice.value).toBe("grid")
    })

    it("returns the query value when it matches one of the choices", () => {
        routeQuery = {layout: "list"}
        const choice = useQueryChoice("layout", ["grid", "list"] as const)
        expect(choice.value).toBe("list")
    })

    it("falls back to the first choice when the query value is outside the choices", () => {
        routeQuery = {layout: "compact"}
        const choice = useQueryChoice("layout", ["grid", "list"] as const)
        expect(choice.value).toBe("grid")
    })

    it("replaces the route with a non-default choice while keeping other query parameters", () => {
        routeQuery = {q: "http", page: "2", layout: "grid"}
        const choice = useQueryChoice("layout", ["grid", "list"] as const)

        choice.value = "list"

        expect(replace).toHaveBeenCalledTimes(1)
        expect(replace).toHaveBeenCalledWith({
            query: {q: "http", page: "2", layout: "list"},
        })
    })

    it("writes undefined for the key when setting the default choice so it drops out of the URL", () => {
        routeQuery = {q: "http", layout: "list"}
        const choice = useQueryChoice("layout", ["grid", "list"] as const)

        choice.value = "grid"

        expect(replace).toHaveBeenCalledTimes(1)
        expect(replace).toHaveBeenCalledWith({
            query: {q: "http", layout: undefined},
        })
    })
})

describe("usePluginView", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        routeQuery = {}
    })

    it("reads the view key with cards as default and accepts table", () => {
        expect(usePluginView().value).toBe("cards")

        routeQuery = {view: "table"}
        expect(usePluginView().value).toBe("table")

        routeQuery = {view: "unknown"}
        expect(usePluginView().value).toBe("cards")
    })

    it("updates or clears the view query key via router.replace", () => {
        routeQuery = {q: "aws"}
        const view = usePluginView()

        view.value = "table"
        expect(replace).toHaveBeenLastCalledWith({
            query: {q: "aws", view: "table"},
        })

        routeQuery = {q: "aws", view: "table"}
        view.value = "cards"
        expect(replace).toHaveBeenLastCalledWith({
            query: {q: "aws", view: undefined},
        })
    })
})
