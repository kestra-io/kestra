import {describe, it, expect} from "vitest"
import type {RouteLocationNormalized} from "vue-router"
import {pageFromRoute} from "../../../src/utils/eventsRouter"

const makeRoute = (overrides: Partial<RouteLocationNormalized> = {}) => ({
    path: "/test",
    fullPath: "/test",
    name: "test-route",
    params: {},
    query: {},
    hash: "",
    ...overrides,
}) as unknown as RouteLocationNormalized

describe("pageFromRoute", () => {
    it("should copy path, fullPath, and name from the route", () => {
        const route = makeRoute({
            path: "/custom-path",
            fullPath: "/custom-full-path",
            name: "custom-name",
        })
        const result = pageFromRoute(route)

        expect(result.path).toBe("/custom-path")
        expect(result.fullPath).toBe("/custom-full-path")
        expect(result.name).toBe("custom-name")
    })

    it("should transform route params into a list of {key, value} pairs", () => {
        const route = makeRoute({
            params: {
                id: "123",
                type: "flow",
            },
        })
        const result = pageFromRoute(route)

        expect(result.params).toEqual([
            {key: "id", value: "123"},
            {key: "type", value: "flow"},
        ])
    })

    it("should transform single-value queries into a list of {key, values: [value]}", () => {
        const route = makeRoute({
            query: {
                tab: "executions",
                sort: "desc",
            },
        })
        const result = pageFromRoute(route)

        expect(result.queries).toEqual([
            {key: "tab", values: ["executions"]},
            {key: "sort", values: ["desc"]},
        ])
    })

    it("should keep all values for array queries", () => {
        const route = makeRoute({
            query: {
                labels: [
                    "env:prod",
                    "team:backend",
                ],
                status: [
                    "SUCCESS",
                    "FAILED",
                ],
            },
        })
        const result = pageFromRoute(route)

        expect(result.queries).toEqual([
            {
                key: "labels",
                values: [
                    "env:prod",
                    "team:backend",
                ],
            },
            {
                key: "status",
                values: [
                    "SUCCESS",
                    "FAILED",
                ],
            },
        ])
    })

    it("should omit an empty hash from the output", () => {
        const route = makeRoute({
            hash: "",
        })
        const result = pageFromRoute(route)

        expect(result.hash).toBeUndefined()
    })

    it("should preserve a non-empty hash in the output", () => {
        const route = makeRoute({
            hash: "#details",
        })
        const result = pageFromRoute(route)

        expect(result.hash).toBe("#details")
    })

    it("should get origin from window.location.origin", () => {
        const route = makeRoute()
        const result = pageFromRoute(route)

        expect(result.origin).toBe(window.location.origin)
    })

    it("should yield empty lists for a route with no params and no query", () => {
        const route = makeRoute()
        const result = pageFromRoute(route)

        expect(result.params).toEqual([])
        expect(result.queries).toEqual([])
    })
})
