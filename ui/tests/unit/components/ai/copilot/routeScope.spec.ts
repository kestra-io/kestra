import {describe, it, expect} from "vitest"
import {scopeFromRoute, scopeToContext} from "../../../../../src/components/ai/copilot/routeScope"

describe("scopeFromRoute", () => {
    it("maps an execution detail route to an EXECUTION scope", () => {
        expect(scopeFromRoute({name: "executions/update", params: {namespace: "company.team", flowId: "my-flow", id: "exec-1"}}))
            .toEqual({kind: "EXECUTION", namespace: "company.team", flowId: "my-flow", executionId: "exec-1"})
    })

    it("maps a flow detail route to a FLOW scope (id is the flow id)", () => {
        expect(scopeFromRoute({name: "flows/update", params: {namespace: "company.team", id: "my-flow"}}))
            .toEqual({kind: "FLOW", namespace: "company.team", flowId: "my-flow"})
    })

    it("maps a namespace detail route to a NAMESPACE scope (id is the namespace)", () => {
        expect(scopeFromRoute({name: "namespaces/update", params: {id: "company.team"}}))
            .toEqual({kind: "NAMESPACE", namespace: "company.team"})
    })

    it("returns null for routes with no meaningful scope", () => {
        expect(scopeFromRoute({name: "flows/list", params: {}})).toBeNull()
        expect(scopeFromRoute({name: "home", params: {}})).toBeNull()
    })

    it("is defensive about missing/oddly-typed route input", () => {
        expect(scopeFromRoute(undefined)).toBeNull()
        expect(scopeFromRoute(null)).toBeNull()
        expect(scopeFromRoute({params: {}})).toBeNull()
        // A symbol name (Vue Router allows it) is not one of ours → null.
        expect(scopeFromRoute({name: Symbol("x"), params: {}})).toBeNull()
    })

    it("takes the first value when a param is an array and treats empty as absent", () => {
        expect(scopeFromRoute({name: "flows/update", params: {namespace: ["a", "b"], id: "f"}}))
            .toEqual({kind: "FLOW", namespace: "a", flowId: "f"})
        expect(scopeFromRoute({name: "namespaces/update", params: {id: ""}}))
            .toEqual({kind: "NAMESPACE", namespace: undefined})
    })
})

describe("scopeToContext", () => {
    it("wraps a scope as a currentView additionalContext map, omitting absent fields", () => {
        expect(scopeToContext({kind: "EXECUTION", namespace: "company.team", flowId: "my-flow", executionId: "exec-1"}))
            .toEqual({currentView: {kind: "EXECUTION", namespace: "company.team", flowId: "my-flow", executionId: "exec-1"}})
        expect(scopeToContext({kind: "NAMESPACE", namespace: "company.team"}))
            .toEqual({currentView: {kind: "NAMESPACE", namespace: "company.team"}})
    })

    it("returns undefined when there is no scope", () => {
        expect(scopeToContext(null)).toBeUndefined()
        expect(scopeToContext(undefined)).toBeUndefined()
    })
})
