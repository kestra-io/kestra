import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {createMemoryHistory, createRouter} from "vue-router"
import {openFlowInNewTab} from "../../../src/utils/openFlow"

const Empty = {template: "<div />"}

async function testRouter(tenant?: string) {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [
            {name: "home", path: "/:tenant?", component: Empty},
            {name: "flows/update/edit", path: "/:tenant?/flows/edit/:namespace/:id", component: Empty},
            {name: "executions/update/topology", path: "/:tenant?/executions/:namespace/:flowId/:id/topology", component: Empty},
        ],
    })
    await router.push({name: "home", params: {tenant}})
    vi.spyOn(router, "resolve")
    return router
}

describe("openFlowInNewTab", () => {
    let openSpy: ReturnType<typeof vi.spyOn>

    beforeEach(() => {
        openSpy = vi.spyOn(window, "open").mockImplementation(() => null)
    })

    afterEach(() => {
        openSpy.mockRestore()
    })

    it("opens the flow edit route in a new browser tab, preserving the tenant", async () => {
        const router = await testRouter("main")

        openFlowInNewTab({namespace: "company.team", flowId: "child_flow", tab: "edit"}, router)

        expect(router.resolve).toHaveBeenCalledWith({
            name: "flows/update/edit",
            params: {
                namespace: "company.team",
                id: "child_flow",
                tenant: "main",
            },
        })
        expect(openSpy).toHaveBeenCalledWith("/main/flows/edit/company.team/child_flow", "_blank")
    })

    it("opens the execution topology when an executionId is given", async () => {
        const router = await testRouter("main")

        openFlowInNewTab(
            {namespace: "company.team", flowId: "child_flow", executionId: "exec-123"},
            router,
        )

        expect(router.resolve).toHaveBeenCalledWith({
            name: "executions/update/topology",
            params: {
                namespace: "company.team",
                flowId: "child_flow",
                id: "exec-123",
                tenant: "main",
            },
        })
        expect(openSpy).toHaveBeenCalledWith("/main/executions/company.team/child_flow/exec-123/topology", "_blank")
    })

    it("still opens a new tab when there is no tenant (OSS)", async () => {
        const router = await testRouter(undefined)

        openFlowInNewTab({namespace: "company.team", flowId: "child_flow", tab: "edit"}, router)

        expect(router.resolve).toHaveBeenCalledWith({
            name: "flows/update/edit",
            params: {
                namespace: "company.team",
                id: "child_flow",
                tenant: undefined,
            },
        })
        expect(openSpy).toHaveBeenCalledWith("/flows/edit/company.team/child_flow", "_blank")
    })
})
