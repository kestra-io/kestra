import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createMemoryHistory, createRouter} from "vue-router"

import {i18nMount} from "../../../../../tests/unit/i18nMount"
import ParentExecutionLink from "./ParentExecutionLink.vue"
import type {Execution} from "../../../../stores/executions"

const mocks = vi.hoisted(() => ({
    fetchExecution: vi.fn(),
    toastError: vi.fn(),
}))

vi.mock("../../../../stores/executions", () => ({
    useExecutionsStore: () => ({fetchExecution: mocks.fetchExecution}),
}))

vi.mock("../../../../utils/toast", () => ({
    useToast: () => ({error: mocks.toastError}),
}))

const childExecution = (parentId?: string) => ({
    id: "child-execution",
    namespace: "child-namespace",
    flowId: "child-flow",
    parentId,
} as Execution)

async function mountLink(execution: Execution) {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [
            {name: "current", path: "/current", component: {template: "<div />"}},
            {
                name: "executions/update/overview",
                path: "/:tenant?/executions/:namespace/:flowId/:id/overview",
                component: {template: "<div />"},
            },
        ],
    })
    await router.push({name: "current"})

    const wrapper = i18nMount(ParentExecutionLink, {
        props: {execution},
        messages: {
            "parent execution": "Parent execution",
            error: "Error",
        },
        global: {plugins: [router]},
    })

    return {router, wrapper}
}

describe("ParentExecutionLink", () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it("shouldHideWhenExecutionHasNoParent", async () => {
        const {wrapper} = await mountLink(childExecution())

        expect(wrapper.find("button").exists()).toBe(false)
    })

    it("shouldNavigateToFetchedParentExecution", async () => {
        mocks.fetchExecution.mockResolvedValue({
            id: "parent-execution",
            namespace: "parent-namespace",
            flowId: "parent-flow",
            tenantId: "parent-tenant",
        } as Execution)
        const {router, wrapper} = await mountLink(childExecution("parent-execution"))

        expect(wrapper.text()).toContain("Parent execution: parent-execution")

        await wrapper.get("button").trigger("click")
        await flushPromises()

        expect(mocks.fetchExecution).toHaveBeenCalledWith({id: "parent-execution"})
        expect(router.currentRoute.value.name).toBe("executions/update/overview")
        expect(router.currentRoute.value.params).toMatchObject({
            namespace: "parent-namespace",
            flowId: "parent-flow",
            id: "parent-execution",
            tenant: "parent-tenant",
        })
    })

    it("shouldShowErrorWhenParentExecutionCannotBeLoaded", async () => {
        mocks.fetchExecution.mockRejectedValue(new Error("not found"))
        const {wrapper} = await mountLink(childExecution("missing-parent"))

        await wrapper.get("button").trigger("click")
        await flushPromises()

        expect(mocks.toastError).toHaveBeenCalledWith("Error")
    })
})
