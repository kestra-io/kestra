import {describe, expect, it} from "vitest"
import {loginRedirectHint} from "./loginRedirectHint"

describe("loginRedirectHint", () => {
    it("reads namespace, flow, execution and tab from an execution path", () => {
        expect(loginRedirectHint("/executions/company.finance/monthly_close/2qncB78egtgMA8D0HVACE2/gantt?x=1"))
            .toEqual({namespace: "company.finance", flowId: "monthly_close", executionId: "2qncB78egtgMA8D0HVACE2", tabLabelKey: "gantt"})
    })

    it("accepts a tenant prefix and defaults to the overview tab", () => {
        expect(loginRedirectHint("/main/executions/ns/flow/abc"))
            .toEqual({namespace: "ns", flowId: "flow", executionId: "abc", tabLabelKey: "overview"})
    })

    it("reads a flow path without an execution", () => {
        expect(loginRedirectHint("/flows/edit/company.team/nested/topology"))
            .toEqual({namespace: "company.team", flowId: "nested", tabLabelKey: "topology"})
    })

    it("drops a tab that is not a known slug", () => {
        expect(loginRedirectHint("/executions/ns/flow/abc/constructor")).toMatchObject({tabLabelKey: undefined})
        expect(loginRedirectHint("/executions/ns/flow/abc/__proto__")).toMatchObject({tabLabelKey: undefined})
        expect(loginRedirectHint("/flows/edit/ns/flow/<img src=x>")).toMatchObject({tabLabelKey: undefined})
    })

    it.each([
        undefined,
        "",
        "/dashboard",
        "/executions",
        "/namespaces/edit/company",
        "/executions/ns/<script>alert(1)</script>/abc/gantt",
        "/executions/ns/flow/abc%20def/gantt",
        "/executions/ns/flow/abc/gantt/extra",
        "//evil.com/executions/ns/flow/abc/gantt",
        "/executions/ns/flow/abc\n/gantt",
        "/flows/edit/ns/flow/overview/../../x",
    ])("returns nothing for %s", (path) => {
        expect(loginRedirectHint(path)).toBeUndefined()
    })
})
