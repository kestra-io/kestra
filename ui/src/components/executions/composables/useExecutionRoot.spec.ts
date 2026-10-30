import {beforeEach, describe, expect, it, vi} from "vitest"
import {reactive} from "vue"
import {flushPromises, mount} from "@vue/test-utils"
import type {Execution} from "@kestra-io/kestra-sdk"

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

const mockedExecutionsStore = reactive({
    execution: undefined as Execution | undefined,
    logs: [],
    resetLogs: vi.fn(),
    closeSSE: vi.fn(),
    followExecution,
})

vi.mock("../../../stores/executions", () => ({
    useExecutionsStore: () => mockedExecutionsStore,
}))

vi.mock("../executionTabs", () => ({
    EXECUTION_PARENT_ROUTE: "executions/update",
    EXECUTION_TAB_ROUTES: [],
}))

import {useFlowStore, type Flow} from "../../../stores/flow"
import type {FlowGraph} from "@kestra-io/topology/vue-flow-utils"
import {useExecutionRoot, buildLoopBreadcrumbs} from "./useExecutionRoot"
import {useExecutionsStore} from "../../../stores/executions"

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

function mockExecution(overrides: Partial<Execution>): Execution {
    return {
        id: "default-id",
        originalId: "default-id",
        namespace: "company.team",
        flowId: "demo_flow",
        flowRevision: 1,
        metadata: {
            originalCreatedDate: "2026-01-01T00:00:00Z",
            attemptNumber: 1,
        },
        inputs: {},
        taskRunList: [],
        state: {
            current: "SUCCESS",
            histories: [],
            startDate: "2026-01-01T00:00:00Z",
            duration: "PT1S",
            getStartDate: "2026-01-01T00:00:00Z",
            getEndDate: "",
            getDuration: "PT1S",
        },
        ...overrides,
    } as Execution
}

describe("useExecutionRoot breadcrumbs", () => {
    const executionsStore = useExecutionsStore()

    beforeEach(() => {
        route.params = {namespace: "company.team", flowId: "demo_flow", id: "exec-2"}
        executionsStore.execution = undefined
    })

    it("builds the breadcrumb without loop details for standard execution", () => {
        executionsStore.execution = mockExecution({
            id: "exec-standard",
        })

        const root = useExecutionRoot()
        const breadcrumb = "breadcrumb" in root.routeInfo.value ? root.routeInfo.value.breadcrumb! : []

        expect(breadcrumb.length).toBe(4)

        const lastCrumb = breadcrumb[3]
        expect((lastCrumb.link as {name: string}).name).toBe("flows/update")
    })

    it("builds the root fallback breadcrumb for a single-level loop", () => {
        route.params.id = "exec-1"
        executionsStore.execution = mockExecution({
            id: "exec-1",
            loopRun: {
                rootExecutionId: "exec-root",
                taskId: "regions",
                value: "EMEA",
                parents: [],
            },
        })

        const root = useExecutionRoot()
        const breadcrumb = "breadcrumb" in root.routeInfo.value ? root.routeInfo.value.breadcrumb! : []

        expect(breadcrumb.length).toBe(6)

        const rootCrumb = breadcrumb[4]
        expect(rootCrumb.label).toBe("root_execution")
        expect((rootCrumb.link as {params: {id: string}}).params.id).toBe("exec-root")

        const currentCrumb = breadcrumb[5]
        expect(currentCrumb.label).toBe("regions (EMEA)")
        expect((currentCrumb.link as {params: {id: string}}).params.id).toBe("exec-1")
    })

    it("builds the root fallback and parents breadcrumbs for a nested loop", () => {
        executionsStore.execution = mockExecution({
            id: "exec-2",
            loopRun: {
                rootExecutionId: "exec-root",
                taskId: "quarters",
                value: "Q1",
                parents: [
                    {
                        executionId: "exec-1",
                        taskId: "regions",
                        value: "EMEA",
                    },
                ],
            },
        })

        const root = useExecutionRoot()
        const breadcrumb = "breadcrumb" in root.routeInfo.value ? root.routeInfo.value.breadcrumb! : []

        expect(breadcrumb.length).toBe(7)

        const rootCrumb = breadcrumb[4]
        expect(rootCrumb.label).toBe("root_execution")
        expect((rootCrumb.link as {params: {id: string}}).params.id).toBe("exec-root")

        const parentCrumb = breadcrumb[5]
        expect(parentCrumb.label).toBe("regions (EMEA)")
        expect((parentCrumb.link as {params: {id: string}}).params.id).toBe("exec-1")

        const currentCrumb = breadcrumb[6]
        expect(currentCrumb.label).toBe("quarters (Q1)")
        expect((currentCrumb.link as {params: {id: string}}).params.id).toBe("exec-2")
    })

    it("falls back to parentId when rootExecutionId is absent", () => {
        route.params.id = "exec-historical"
        executionsStore.execution = mockExecution({
            id: "exec-historical",
            parentId: "exec-parent",
            loopRun: {
                taskId: "regions",
                value: "EMEA",
                parents: [],
            },
        })

        const root = useExecutionRoot()
        const breadcrumb = "breadcrumb" in root.routeInfo.value ? root.routeInfo.value.breadcrumb! : []

        expect(breadcrumb.length).toBe(6)

        const rootCrumb = breadcrumb[4]
        expect(rootCrumb.label).toBe("root_execution")
        expect((rootCrumb.link as {params: {id: string}}).params.id).toBe("exec-parent")

        const currentCrumb = breadcrumb[5]
        expect(currentCrumb.label).toBe("regions (EMEA)")
        expect((currentCrumb.link as {params: {id: string}}).params.id).toBe("exec-historical")
    })

    it("does not fall back to parentId for legacy nested loop when rootExecutionId is absent", () => {
        route.params.id = "exec-historical-nested"
        executionsStore.execution = mockExecution({
            id: "exec-historical-nested",
            parentId: "exec-level1",
            loopRun: {
                taskId: "quarters",
                value: "Q1",
                parents: [
                    {
                        executionId: "exec-level1",
                        taskId: "regions",
                        value: "EMEA",
                    },
                ],
            },
        })

        const root = useExecutionRoot()
        const breadcrumb = "breadcrumb" in root.routeInfo.value ? root.routeInfo.value.breadcrumb! : []

        expect(breadcrumb.some(b => b.label === "root_execution")).toBe(false)
    })

    it("truncates long loop values in breadcrumbs", () => {
        route.params.id = "exec-1"
        executionsStore.execution = mockExecution({
            id: "exec-1",
            loopRun: {
                rootExecutionId: "exec-root",
                taskId: "process",
                value: "{\"key\": \"very-long-json-payload-that-exceeds-limit\"}",
                parents: [],
            },
        })

        const root = useExecutionRoot()
        const breadcrumb = "breadcrumb" in root.routeInfo.value ? root.routeInfo.value.breadcrumb! : []

        const currentCrumb = breadcrumb[5]
        expect(currentCrumb.label).toBe("process ({\"key\": \"very-long-j…)")
    })

    it("omits loop breadcrumbs when execution in store does not match active route id", () => {
        route.params.id = "exec-route-id"
        executionsStore.execution = mockExecution({
            id: "exec-store-id",
            loopRun: {
                rootExecutionId: "exec-root",
                taskId: "regions",
                value: "EMEA",
                parents: [],
            },
        })

        const root = useExecutionRoot()
        const breadcrumb = "breadcrumb" in root.routeInfo.value ? root.routeInfo.value.breadcrumb! : []

        expect(breadcrumb.length).toBe(4)
        expect(breadcrumb.some(b => b.label === "root_execution")).toBe(false)
    })
})

describe("buildLoopBreadcrumbs", () => {
    const t = (k: string) => k

    it("returns empty array when execution has no loopRun", () => {
        const execution = mockExecution({id: "exec-standard"})
        expect(buildLoopBreadcrumbs(execution, "ns", "flow", t)).toEqual([])
    })

    it("builds loop breadcrumbs directly", () => {
        const execution = mockExecution({
            id: "exec-1",
            loopRun: {
                rootExecutionId: "exec-root",
                taskId: "regions",
                value: "EMEA",
                parents: [],
            },
        })
        const crumbs = buildLoopBreadcrumbs(execution, "ns", "flow", t)
        expect(crumbs.length).toBe(2)
        expect(crumbs[0].label).toBe("root_execution")
        expect(crumbs[1].label).toBe("regions (EMEA)")
    })
})
