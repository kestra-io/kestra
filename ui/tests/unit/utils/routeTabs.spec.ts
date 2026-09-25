import {describe, expect, it} from "vitest"
import type {RouteRecordRaw} from "vue-router"
import {resolveDefaultTab} from "../../../src/utils/routeTabs"

const tabRoutes = [{meta: {tab: "overview"}}, {meta: {tab: "edit"}}] as RouteRecordRaw[]

describe("resolveDefaultTab", () => {
    it("returns the requested tab when a route carries a matching meta.tab", () => {
        expect(resolveDefaultTab(tabRoutes, "edit", "overview")).toBe("edit")
    })

    it("returns the fallback when no route matches", () => {
        expect(resolveDefaultTab(tabRoutes, "stale", "overview")).toBe("overview")
    })

    it("returns the fallback for null and undefined requested values", () => {
        expect(resolveDefaultTab(tabRoutes, null, "overview")).toBe("overview")
        expect(resolveDefaultTab(tabRoutes, undefined, "overview")).toBe("overview")
    })

    it("returns the fallback when tabRoutes is empty", () => {
        expect(resolveDefaultTab([], "edit", "overview")).toBe("overview")
    })

    it("does not throw on routes without a meta object", () => {
        const mixed = [{path: "a"}, {meta: {tab: "edit"}}] as RouteRecordRaw[]
        expect(() => resolveDefaultTab(mixed, "edit", "overview")).not.toThrow()
        expect(resolveDefaultTab(mixed, "edit", "overview")).toBe("edit")
        expect(resolveDefaultTab([{path: "a"}] as RouteRecordRaw[], "edit", "overview")).toBe("overview")
    })
})
