import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {createMemoryHistory, createRouter} from "vue-router"
import {openFlowInNewTab} from "../../../src/utils/openFlow"

function makeRouter() {
    return createRouter({
        history: createMemoryHistory(),
        routes: [
            {name: "flows/update/edit", path: "/:tenant?/flows/:namespace/:id/:tab?", component: {template: "<div />"}},
            {name: "executions/update/topology", path: "/:tenant?/executions/:namespace/:flowId/:id/:tab?", component: {template: "<div />"}},
        ],
    })
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
        const router = makeRouter()
        const resolveSpy = vi.spyOn(router, "resolve")
        await router.push({name: "flows/update/edit", params: {tenant: "main", namespace: "company.team", id: "root", tab: "edit"}})

        openFlowInNewTab({namespace: "company.team", flowId: "child_flow", tab: "edit"}, router)

        expect(resolveSpy).toHaveBeenCalledWith({
            name: "flows/update/edit",
            params: {
                namespace: "company.team",
                id: "child_flow",
                tenant: "main",
            },
        })
        expect(openSpy).toHaveBeenCalledWith(router.resolve({
            name: "flows/update/edit",
            params: {
                namespace: "company.team",
                id: "child_flow",
                tenant: "main",
            },
        }).href, "_blank")
    })

    it("opens the execution topology when an executionId is given", async () => {
        const router = makeRouter()
        const resolveSpy = vi.spyOn(router, "resolve")
        await router.push({name: "flows/update/edit", params: {tenant: "main", namespace: "company.team", id: "root", tab: "edit"}})

        openFlowInNewTab(
            {namespace: "company.team", flowId: "child_flow", executionId: "exec-123"},
            router,
        )

        expect(resolveSpy).toHaveBeenCalledWith({
            name: "executions/update/topology",
            params: {
                namespace: "company.team",
                flowId: "child_flow",
                id: "exec-123",
                tenant: "main",
            },
        })
        expect(openSpy).toHaveBeenCalledWith(router.resolve({
            name: "executions/update/topology",
            params: {
                namespace: "company.team",
                flowId: "child_flow",
                id: "exec-123",
                tenant: "main",
            },
        }).href, "_blank")
    })

    it("still opens a new tab when there is no tenant (OSS)", async () => {
        const router = makeRouter()
        const resolveSpy = vi.spyOn(router, "resolve")
        await router.push({name: "flows/update/edit", params: {namespace: "company.team", id: "root", tab: "edit"}})

        openFlowInNewTab({namespace: "company.team", flowId: "child_flow", tab: "edit"}, router)

        expect(resolveSpy).toHaveBeenCalledWith({
            name: "flows/update/edit",
            params: {
                namespace: "company.team",
                id: "child_flow",
                tenant: undefined,
            },
        })
        expect(openSpy).toHaveBeenCalledWith(router.resolve({
            name: "flows/update/edit",
            params: {
                namespace: "company.team",
                id: "child_flow",
                tenant: undefined,
            },
        }).href, "_blank")
    })
})
