import {beforeEach, describe, expect, it, vi} from "vitest"
import {reactive} from "vue"
import {flushPromises, mount} from "@vue/test-utils"

const route = reactive<{params: Record<string, string>}>({
    params: {namespace: "company.team", flowId: "demo_breadcrumb_fix", id: "exec-1"},
})

// Hoisted: the store factory below hands this spy to every consumer of the store, so the
// expectations below can read the calls the composable made.
const {followExecution} = vi.hoisted(() => ({followExecution: vi.fn()}))

vi.mock("vue-router", () => ({
    useRoute: () => route,
}))

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => key}),
}))

vi.mock("../../../stores/flow", async () => {
    const {reactive: reactiveVue} = await import("vue")
    const flowStore = reactiveVue({
        flow: undefined,
        flowGraph: undefined,
        loadDependencies: vi.fn().mockResolvedValue({count: 0}),
    })
    return {useFlowStore: () => flowStore}
})

vi.mock("../../../stores/executions", () => ({
    useExecutionsStore: () => ({
        execution: undefined,
        logs: [],
        resetLogs: vi.fn(),
        closeSSE: vi.fn(),
        followExecution,
    }),
}))

vi.mock("../executionTabs", () => ({
    EXECUTION_PARENT_ROUTE: "executions/update",
    EXECUTION_TAB_ROUTES: [],
}))

import {useFlowStore} from "../../../stores/flow"
import {useExecutionRoot} from "./useExecutionRoot"

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

describe("useExecutionRoot follow", () => {
    beforeEach(() => {
        route.params = {namespace: "company.team", flowId: "demo_breadcrumb_fix", id: "exec-1"}
        followExecution.mockClear()
    })

    // The store follows one execution, identified by its id alone: namespace and flowId describe
    // the flow, so forwarding them would subscribe to nothing.
    it("follows the execution the route points at, by id", () => {
        const wrapper = mountExecutionRoot()

        expect(followExecution).toHaveBeenCalledWith({id: "exec-1"}, expect.any(Function))

        wrapper.unmount()
    })

    it("follows the execution the route moves to", async () => {
        const wrapper = mountExecutionRoot()
        // Let the mount hook finish before moving the route: it records the id it followed after
        // its first await, which would otherwise land once the watcher has already run.
        await flushPromises()

        route.params = {namespace: "company.team", flowId: "demo_breadcrumb_fix", id: "exec-2"}
        await flushPromises()

        expect(followExecution).toHaveBeenLastCalledWith({id: "exec-2"}, expect.any(Function))

        wrapper.unmount()
    })
})
