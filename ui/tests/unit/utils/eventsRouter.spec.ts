import {describe, expect, it} from "vitest"
import {pageFromRoute} from "../../../src/utils/eventsRouter"
import type {RouteLocationNormalized} from "vue-router"

describe("pageFromRoute", () => {
    it("copies path, fullPath, and name directly from the provided route object", () => {
        const route = {
            path: "/test/path",
            fullPath: "/test/path?q=1",
            name: "testRoute",
            params: {},
            query: {},
            hash: "",
        } as unknown as RouteLocationNormalized

        const result = pageFromRoute(route)

        expect(result.path).toEqual("/test/path")
        expect(result.fullPath).toEqual("/test/path?q=1")
        expect(result.name).toEqual("testRoute")
    })

    it("converts route parameters into a list of objects formatted as {key, value}", () => {
        const route = {
            path: "/test/path",
            fullPath: "/test/path",
            params: {
                id: "123",
                type: "flow",
            },
            query: {},
            hash: "",
        } as unknown as RouteLocationNormalized

        const result = pageFromRoute(route)

        expect(result.params).toEqual([
            {key: "id", value: "123"},
            {key: "type", value: "flow"},
        ])
    })

    it("transforms a single-value query into {key, values: [value]}", () => {
        const route = {
            path: "/test/path",
            fullPath: "/test/path",
            params: {},
            query: {
                search: "kestra",
            },
            hash: "",
        } as unknown as RouteLocationNormalized

        const result = pageFromRoute(route)

        expect(result.queries).toEqual([
            {key: "search", values: ["kestra"]},
        ])
    })

    it("retains all of its values correctly for an array query", () => {
        const route = {
            path: "/test/path",
            fullPath: "/test/path",
            params: {},
            query: {
                tags: ["tag1", "tag2"],
            },
            hash: "",
        } as unknown as RouteLocationNormalized

        const result = pageFromRoute(route)

        expect(result.queries).toEqual([
            {key: "tags", values: ["tag1", "tag2"]},
        ])
    })

    it("omits an empty hash completely from the output, whereas a non-empty hash is kept", () => {
        const routeEmptyHash = {
            path: "/test/path",
            fullPath: "/test/path",
            params: {},
            query: {},
            hash: "",
        } as unknown as RouteLocationNormalized

        const resultEmpty = pageFromRoute(routeEmptyHash)
        expect(resultEmpty.hash).toBeUndefined()

        const routeWithHash = {
            path: "/test/path",
            fullPath: "/test/path",
            params: {},
            query: {},
            hash: "#section1",
        } as unknown as RouteLocationNormalized

        const resultWithHash = pageFromRoute(routeWithHash)
        expect(resultWithHash.hash).toEqual("#section1")
    })

    it("extracts the origin property successfully from window.location.origin", () => {
        const route = {
            path: "/test/path",
            fullPath: "/test/path",
            params: {},
            query: {},
            hash: "",
        } as unknown as RouteLocationNormalized

        const result = pageFromRoute(route)
        
        expect(result.origin).toEqual(window.location.origin)
    })

    it("yields empty lists ([]) instead of undefined if a route has no parameters and no queries", () => {
        const route = {
            path: "/test/path",
            fullPath: "/test/path",
            params: {},
            query: {},
            hash: "",
        } as unknown as RouteLocationNormalized

        const result = pageFromRoute(route)
        
        expect(result.params).toEqual([])
        expect(result.queries).toEqual([])
    })
})
