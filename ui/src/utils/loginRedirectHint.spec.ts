import {describe, expect, it} from "vitest"
import {loginRedirectHint} from "./loginRedirectHint"

describe("loginRedirectHint", () => {
    it("reads namespace, flow, execution and tab from an execution path", () => {
        expect(loginRedirectHint("/executions/company.finance/monthly_close/2qncB78egtgMA8D0HVACE2/gantt?x=1"))
            .toEqual({namespace: "company.finance", flowId: "monthly_close", executionId: "2qncB78egtgMA8D0HVACE2", tab: "gantt"})
    })

    it("accepts a tenant prefix and the app base path", () => {
        expect(loginRedirectHint("/ui/main/executions/ns/flow/abc"))
            .toEqual({namespace: "ns", flowId: "flow", executionId: "abc", tab: undefined})
    })

    it("reads a flow path without an execution", () => {
        expect(loginRedirectHint("/flows/edit/company.team/nested/topology"))
            .toEqual({namespace: "company.team", flowId: "nested", tab: "topology"})
    })

    it.each([undefined, "", "/dashboard", "/executions", "/namespaces/edit/company"])("returns nothing for %s", (path) => {
        expect(loginRedirectHint(path)).toBeUndefined()
    })
})
