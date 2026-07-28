import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {openFlowInNewTab} from "../../../src/utils/openFlow"

function fakeRouter(tenant?: string) {
    return {
        currentRoute: {value: {params: {tenant}}},
        resolve: vi.fn(() => ({href: "/resolved/href"})),
    }
}

describe("openFlowInNewTab", () => {
    let openSpy: ReturnType<typeof vi.spyOn>

    beforeEach(() => {
        openSpy = vi.spyOn(window, "open").mockImplementation(() => null)
    })

    afterEach(() => {
        openSpy.mockRestore()
    })

    it("opens the flow edit route in a new browser tab, preserving the tenant", () => {
        const router = fakeRouter("main")

        openFlowInNewTab({namespace: "company.team", flowId: "child_flow", tab: "edit"}, router as any)

        expect(router.resolve).toHaveBeenCalledWith({
            name: "flows/update",
            params: {
                namespace: "company.team",
                id: "child_flow",
                tab: "edit",
                tenant: "main",
            },
        })
        expect(openSpy).toHaveBeenCalledWith("/resolved/href", "_blank")
    })

    it("opens the execution topology when an executionId is given", () => {
        const router = fakeRouter("main")

        openFlowInNewTab(
            {namespace: "company.team", flowId: "child_flow", executionId: "exec-123"},
            router as any,
        )

        expect(router.resolve).toHaveBeenCalledWith({
            name: "executions/update",
            params: {
                namespace: "company.team",
                flowId: "child_flow",
                tab: "topology",
                id: "exec-123",
                tenant: "main",
            },
        })
        expect(openSpy).toHaveBeenCalledWith("/resolved/href", "_blank")
    })

    it("still opens a new tab when there is no tenant (OSS)", () => {
        const router = fakeRouter(undefined)

        openFlowInNewTab({namespace: "company.team", flowId: "child_flow", tab: "edit"}, router as any)

        expect(router.resolve).toHaveBeenCalledWith({
            name: "flows/update",
            params: {
                namespace: "company.team",
                id: "child_flow",
                tab: "edit",
                tenant: undefined,
            },
        })
        expect(openSpy).toHaveBeenCalledWith("/resolved/href", "_blank")
    })
})
