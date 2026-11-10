import {beforeEach, describe, expect, it, vi} from "vitest"
import {reactive} from "vue"
import {flushPromises, mount} from "@vue/test-utils"

const route = reactive<{params: Record<string, string>}>({
    params: {namespace: "company.team", flowId: "demo_breadcrumb_fix", id: "exec-1"},
})

// Hoisted: the store factory below hands this spy to every consumer of the store, so the
// expectations below can read the calls the composable made.
const {followExecution, loopLevels} = vi.hoisted(() => ({followExecution: vi.fn(), loopLevels: {value: undefined as unknown}}))

vi.mock("vue-router", () => ({
    useRoute: () => route,
}))

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string, named?: Record<string, unknown>) => named ? `${key}:${Object.values(named).filter((value) => value !== undefined).join("/")}` : key}),
}))

vi.mock("../../../composables/useLoopLineage", () => ({
    useLoopLineage: () => loopLevels,
}))

vi.mock("../../../composables/useNamespaceBreadcrumb", async () => {
    const {computed} = await import("vue")
    return {useNamespaceBreadcrumb: () => computed(() => [{label: "executions"}, {label: "company"}, {label: "team"}])}
})

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

import {useFlowStore, type Flow} from "../../../stores/flow"
import type {FlowGraph} from "@kestra-io/topology/vue-flow-utils"
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
        flowStore.flow = {namespace: "company.team", id: "some-other-flow"} as Flow
        flowStore.flowGraph = {nodes: [], edges: [], clusters: []} as FlowGraph

        const wrapper = mountExecutionRoot()
        route.params = {}
        wrapper.unmount()

        expect(flowStore.flow).toBeUndefined()
        expect(flowStore.flowGraph).toBeUndefined()
    })

    it("keeps the flow store when navigating to that flow's edit page (breadcrumb, #10722)", () => {
        flowStore.flow = {namespace: "company.team", id: "demo_breadcrumb_fix"} as Flow
        flowStore.flowGraph = {nodes: [{uid: "n1", type: "task"}], edges: [], clusters: []} as FlowGraph

        const wrapper = mountExecutionRoot()

        // Router navigation resolves before the outgoing component unmounts: `route` already
        // reflects the flow-edit destination (`namespace`/`id`, no `flowId`) by the time this runs.
        route.params = {namespace: "company.team", id: "demo_breadcrumb_fix"}
        wrapper.unmount()

        expect(flowStore.flow).toEqual({namespace: "company.team", id: "demo_breadcrumb_fix"})
        expect(flowStore.flowGraph).toEqual({nodes: [{uid: "n1", type: "task"}], edges: [], clusters: []})
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

describe("useExecutionRoot breadcrumb", () => {
    function routeInfoOf() {
        let info!: ReturnType<typeof useExecutionRoot>["routeInfo"]
        mount({template: "<div></div>", setup() {
            info = useExecutionRoot().routeInfo
        }})
        return info
    }

    beforeEach(() => {
        route.params = {namespace: "company.team", flowId: "nested_loop_demo", id: "5j2D9NuQvgj0wiyuADhgmt"}
        loopLevels.value = undefined
    })

    it("leaves a top-level execution with the flow as its last crumb and the id as title", () => {
        const info = routeInfoOf().value

        expect(info.title).toBe("5j2D9NuQvgj0wiyuADhgmt")
        expect(info.bookmarkLabel).toBe("company.team.nested_loop_demo: 5j2D9NuQvgj0wiyuADhgmt")
        expect(info.breadcrumb?.map((crumb) => crumb.label)).toEqual(["executions", "company", "team", "nested_loop_demo"])
    })

    it("keeps first, the current loop task and the current iteration visible once the bar collapses", () => {
        loopLevels.value = [
            {executionId: "1mubyrnLabcdef", parentExecutionId: "root", taskId: "per_region", number: 1, count: 3, value: "EMEA"},
            {executionId: "5j2D9NuQvgj0wiyuADhgmt", parentExecutionId: "1mubyrnLabcdef", taskId: "per_quarter", number: 2, count: 4, value: "Q2"},
        ]
        const info = routeInfoOf().value
        const labels = info.breadcrumb!.map((crumb) => crumb.label)

        expect(labels).toEqual(["executions", "company", "team", "nested_loop_demo", "per_region", "1mubyrnL loop-lineage.iteration-count-value:1/3/EMEA", "per_quarter"])
        expect([labels[0], labels.at(-1)]).toEqual(["executions", "per_quarter"])
        expect(info.title).toBe("5j2D9NuQvgj0wiyuADhgmt loop-lineage.iteration-count-value:2/4/Q2")
    })
})
