import {beforeEach, describe, expect, it, vi} from "vitest"
import {reactive} from "vue"
import {mount} from "@vue/test-utils"

const route = reactive<{params: Record<string, string>}>({
    params: {namespace: "company.team", flowId: "demo_breadcrumb_fix", id: "exec-1"},
})

vi.mock("vue-router", () => ({
    useRoute: () => route,
}))

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => key}),
}))

vi.mock("../../../../src/stores/flow", async () => {
    const {reactive: reactiveVue} = await import("vue")
    const flowStore = reactiveVue({
        flow: undefined,
        flowGraph: undefined,
        loadDependencies: vi.fn().mockResolvedValue({count: 0}),
    })
    return {useFlowStore: () => flowStore}
})

vi.mock("../../../../src/stores/executions", () => ({
    useExecutionsStore: () => ({
        execution: undefined,
        logs: {total: 0, results: []},
        closeSSE: vi.fn(),
        followExecution: vi.fn(),
    }),
}))

vi.mock("../../../../src/components/executions/executionTabs", () => ({
    EXECUTION_PARENT_ROUTE: "executions/update",
    EXECUTION_TAB_ROUTES: [],
}))

import {useFlowStore} from "../../../../src/stores/flow"
import {useExecutionRoot} from "../../../../src/components/executions/composables/useExecutionRoot"

function mountExecutionRoot() {
    return mount({
        template: "<div></div>",
        setup() {
            useExecutionRoot().setupLifecycle()
        },
    })
}

describe("useExecutionRoot unmount cleanup", () => {
    const flowStore = useFlowStore()

    beforeEach(() => {
        route.params = {namespace: "company.team", flowId: "demo_breadcrumb_fix", id: "exec-1"}
        flowStore.flow = undefined
        flowStore.flowGraph = undefined
    })

    it("clears the flow store when navigating away from any flow", () => {
        flowStore.flow = {namespace: "company.team", id: "some-other-flow"} as any
        flowStore.flowGraph = {} as any

        const wrapper = mountExecutionRoot()
        route.params = {}
        wrapper.unmount()

        expect(flowStore.flow).toBeUndefined()
        expect(flowStore.flowGraph).toBeUndefined()
    })

    it("keeps the flow store when navigating to that flow's edit page (breadcrumb, #10722)", () => {
        flowStore.flow = {namespace: "company.team", id: "demo_breadcrumb_fix"} as any
        flowStore.flowGraph = {some: "graph"} as any

        const wrapper = mountExecutionRoot()

        // Router navigation resolves before the outgoing component unmounts: `route` already
        // reflects the flow-edit destination (`namespace`/`id`, no `flowId`) by the time this runs.
        route.params = {namespace: "company.team", id: "demo_breadcrumb_fix"}
        wrapper.unmount()

        expect(flowStore.flow).toEqual({namespace: "company.team", id: "demo_breadcrumb_fix"})
        expect(flowStore.flowGraph).toEqual({some: "graph"})
    })
})
