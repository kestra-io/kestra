import {beforeEach, describe, expect, it, vi} from "vitest"
import {reactive} from "vue"
import {mount} from "@vue/test-utils"
import type {Execution} from "@kestra-io/kestra-sdk"

const route = reactive<{params: Record<string, string>}>({
    params: {namespace: "company.team", flowId: "demo_breadcrumb_fix", id: "exec-1"},
})

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
    followExecution: vi.fn(),
})

vi.mock("../../../stores/executions", () => ({
    useExecutionsStore: () => mockedExecutionsStore,
}))

vi.mock("../executionTabs", () => ({
    EXECUTION_PARENT_ROUTE: "executions/update",
    EXECUTION_TAB_ROUTES: [],
}))

import {useFlowStore} from "../../../stores/flow"
import {useExecutionRoot} from "./useExecutionRoot"
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

    it("handles historical loop executions without rootExecutionId gracefully", () => {
        executionsStore.execution = mockExecution({
            id: "exec-historical",
            loopRun: {
                taskId: "regions",
                value: "EMEA",
                parents: [],
            },
        })

        const root = useExecutionRoot()
        const breadcrumb = "breadcrumb" in root.routeInfo.value ? root.routeInfo.value.breadcrumb! : []

        expect(breadcrumb.length).toBe(5)

        const currentCrumb = breadcrumb[4]
        expect(currentCrumb.label).toBe("regions (EMEA)")
        expect((currentCrumb.link as {params: {id: string}}).params.id).toBe("exec-historical")
    })
})
