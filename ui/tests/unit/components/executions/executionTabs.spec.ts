import {afterEach, beforeEach, describe, expect, it} from "vitest"

import {
    DEFAULT_EXECUTION_TAB,
    DEFAULT_TAB_STORAGE_KEY,
    EXECUTION_PARENT_ROUTE,
    EXECUTION_ROUTE,
    EXECUTION_TAB_ROUTES,
} from "../../../../src/components/executions/executionTabs"

// The redirect is a pure function of `to` plus localStorage, so it can be called directly.
const redirectTo = (params: Record<string, string> = {}, query: Record<string, string> = {}) => {
    const redirect = EXECUTION_ROUTE.redirect as (to: unknown) => {name: string; params: unknown; query: unknown}
    return redirect({params, query})
}

describe("EXECUTION_ROUTE redirect", () => {
    beforeEach(() => localStorage.clear())
    afterEach(() => localStorage.clear())

    it("should send an execution to the stored default tab", () => {
        localStorage.setItem(DEFAULT_TAB_STORAGE_KEY, "logs")

        expect(redirectTo().name).toBe(`${EXECUTION_PARENT_ROUTE}/logs`)
    })

    // The three places that answer "no preference set" have to agree, or triggering a flow and
    // replaying one land on different tabs while Settings claims a third answer.
    it("should fall back to the shared default with nothing stored", () => {
        expect(redirectTo().name).toBe(`${EXECUTION_PARENT_ROUTE}/${DEFAULT_EXECUTION_TAB}`)
    })

    it("should fall back to the shared default for a tab that no longer exists", () => {
        localStorage.setItem(DEFAULT_TAB_STORAGE_KEY, "topology-that-was-renamed")

        expect(redirectTo().name).toBe(`${EXECUTION_PARENT_ROUTE}/${DEFAULT_EXECUTION_TAB}`)
    })

    // A legacy deep link names its tab in the params, which has to win over the stored preference.
    it("should honour an explicitly requested tab over the stored one", () => {
        localStorage.setItem(DEFAULT_TAB_STORAGE_KEY, "logs")

        expect(redirectTo({tab: "overview"}).name).toBe(`${EXECUTION_PARENT_ROUTE}/overview`)
    })

    // `resolveDefaultTab` returns its fallback verbatim without checking it exists, so a typo in
    // the constant would build a route name no record matches.
    it("should default to a tab that is actually a route", () => {
        expect(EXECUTION_TAB_ROUTES.some((route) => route.meta?.tab === DEFAULT_EXECUTION_TAB)).toBe(true)
    })

    it("should carry params and query through to the resolved tab", () => {
        const resolved = redirectTo({namespace: "company.team", flowId: "etl", id: "abc"}, {page: "2"})

        expect(resolved.params).toEqual({namespace: "company.team", flowId: "etl", id: "abc"})
        expect(resolved.query).toEqual({page: "2"})
    })
})
