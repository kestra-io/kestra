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

    it("maps the dashboard / app / test / blueprint / plugin detail routes to their scopes", () => {
        expect(scopeFromRoute({name: "dashboards/update", params: {dashboard: "my-dash"}}))
            .toEqual({kind: "DASHBOARD", dashboardId: "my-dash"})
        expect(scopeFromRoute({name: "apps/update", params: {id: "my-app"}}))
            .toEqual({kind: "APP", appId: "my-app"})
        expect(scopeFromRoute({name: "tests/result", params: {namespace: "company.team", testSuiteId: "suite-1", resultId: "r1"}}))
            .toEqual({kind: "TEST", namespace: "company.team", testId: "suite-1"})
        expect(scopeFromRoute({name: "blueprints/view", params: {kind: "flow", tab: "docs", blueprintId: "bp-1"}}))
            .toEqual({kind: "BLUEPRINT", blueprintId: "bp-1"})
        expect(scopeFromRoute({name: "plugins/view", params: {cls: "io.kestra.plugin.core.log.Log"}}))
            .toEqual({kind: "PLUGIN", pluginId: "io.kestra.plugin.core.log.Log"})
    })

    it("returns null for routes with no meaningful scope", () => {
        expect(scopeFromRoute({name: "flows/list", params: {}})).toBeNull()
        expect(scopeFromRoute({name: "home", params: {}})).toBeNull()
    })

    // kestra-io/kestra-ee#10419: the create page has no saved resource, but must still bind
    // the FLOW kind so the editor buffer can attach as context.
    it("maps the flow create route to a FLOW scope with no ids", () => {
        expect(scopeFromRoute({name: "flows/create", params: {}})).toEqual({kind: "FLOW"})
    })

    it("maps a detail page's actual (child) route name, not just its redirecting parent", () => {
        expect(scopeFromRoute({name: "executions/update/overview", params: {namespace: "company.team", flowId: "my-flow", id: "exec-1"}}))
            .toEqual({kind: "EXECUTION", namespace: "company.team", flowId: "my-flow", executionId: "exec-1"})
        expect(scopeFromRoute({name: "flows/update/edit", params: {namespace: "company.team", id: "my-flow"}}))
            .toEqual({kind: "FLOW", namespace: "company.team", flowId: "my-flow"})
        expect(scopeFromRoute({name: "namespaces/update/overview", params: {id: "company.team"}}))
            .toEqual({kind: "NAMESPACE", namespace: "company.team"})
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

    it("carries the dashboard / test / plugin resource ids into currentView", () => {
        expect(scopeToContext({kind: "DASHBOARD", dashboardId: "my-dash"}))
            .toEqual({currentView: {kind: "DASHBOARD", dashboardId: "my-dash"}})
        expect(scopeToContext({kind: "TEST", namespace: "company.team", testId: "suite-1"}))
            .toEqual({currentView: {kind: "TEST", namespace: "company.team", testId: "suite-1"}})
        expect(scopeToContext({kind: "PLUGIN", pluginId: "io.kestra.plugin.core.log.Log"}))
            .toEqual({currentView: {kind: "PLUGIN", pluginId: "io.kestra.plugin.core.log.Log"}})
    })

    it("returns undefined when there is no scope", () => {
        expect(scopeToContext(null)).toBeUndefined()
        expect(scopeToContext(undefined)).toBeUndefined()
    })

    // kestra-io/kestra-ee#10419: a new or unsaved flow exists only in the editor buffer,
    // so the turn context must carry its source even without a saved flow to reference.
    it("carries the editor's flow source into currentView, with or without a scope", () => {
        expect(scopeToContext({kind: "FLOW", namespace: "company.team", flowId: "my-flow"}, "id: my-flow"))
            .toEqual({currentView: {kind: "FLOW", namespace: "company.team", flowId: "my-flow", flowSource: "id: my-flow"}})
        expect(scopeToContext(null, "id: repro"))
            .toEqual({currentView: {kind: "FLOW", flowSource: "id: repro"}})
    })
})
