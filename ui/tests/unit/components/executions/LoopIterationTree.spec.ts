import {describe, it, expect, vi, beforeEach} from "vitest"
import {mount} from "@vue/test-utils"

const mockFindExecutions = vi.fn()
vi.mock("../../../../src/stores/executions", () => ({
    useExecutionsStore: () => ({
        findExecutions: mockFindExecutions,
        flow: {},
    }),
}))

const mockExecutionApi = vi.fn()
vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    execution: (...args: unknown[]) => mockExecutionApi(...args),
}))

const mockFindTaskById = vi.fn()
vi.mock("../../../../src/utils/flowUtils", () => ({
    findTaskById: (...args: unknown[]) => mockFindTaskById(...args),
}))

import LoopIterationTree from "../../../../src/components/executions/LoopIterationTree.vue"

function iterationExecution(id: string, value: string, state = "SUCCESS") {
    return {
        id,
        namespace: "company.team",
        flowId: "nested_loop_demo",
        state: {
            current: state,
            histories: [
                {state: "RUNNING", date: "2026-01-01T00:00:00Z"},
                {state, date: "2026-01-01T00:00:01Z"},
            ],
        },
        loopRun: {taskId: "per_region", taskRunId: "tr1", index: 0, value, parents: []},
    }
}

const KsButtonStub = {
    name: "KsButton",
    props: ["loading", "tag", "to", "size", "link"],
    emits: ["click"],
    template: '<button class="ks-button-stub" @click="$emit(\'click\')"><slot /></button>',
}

const KsCheckboxStub = {
    name: "KsCheckbox",
    props: ["modelValue"],
    emits: ["update:modelValue"],
    template: '<input type="checkbox" class="ks-checkbox-stub" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />',
}

const stubs = {
    KsAlert: {template: '<div class="ks-alert-stub"><slot /></div>'},
    KsButton: KsButtonStub,
    KsCheckbox: KsCheckboxStub,
    KsIcon: {template: '<div><slot /></div>'},
    KsExecutionStatus: {template: '<span class="status-stub" />'},
    SubFlowLink: {template: '<span class="subflow-link-stub" />'},
    Duration: {template: '<span class="duration-stub" />'},
    TaskRunLine: {props: ["currentTaskRun"], template: '<div class="task-run-line-stub">{{ currentTaskRun.taskId }}<slot /></div>'},
    ChevronRight: {template: '<i />'},
    ChevronDown: {template: '<i />'},
    Loading: {template: '<i />'},
    RouterLink: {template: '<a><slot /></a>'},
}

function mountTree(props = {}) {
    return mount(LoopIterationTree, {
        props: {
            executionId: "root-execution",
            taskId: "per_region",
            namespace: "company.team",
            flowId: "nested_loop_demo",
            ...props,
        },
        global: {
            stubs,
            components: {
                LoopIterationTree,
            },
            mocks: {
                $t: (key: string, params?: Record<string, unknown>) => (params ? `${key}:${JSON.stringify(params)}` : key),
            },
        },
    })
}

describe("LoopIterationTree", () => {
    beforeEach(() => {
        mockFindExecutions.mockReset()
        mockExecutionApi.mockReset()
        mockFindTaskById.mockReset()
        mockFindTaskById.mockReturnValue({type: "io.kestra.plugin.core.flow.Commands"})
    })

    it("does not fetch iterations until the toggle is expanded", async () => {
        mockFindExecutions.mockResolvedValue({results: [], total: 0})
        mountTree()

        expect(mockFindExecutions).not.toHaveBeenCalled()
    })

    it("fetches iterations on first expand, scoped to parentId/kind/taskId", async () => {
        mockFindExecutions.mockResolvedValue({results: [iterationExecution("it-1", "EMEA")], total: 1})
        const wrapper = mountTree()

        await wrapper.find('[data-test="loop-iteration-toggle"]').trigger("click")
        await wrapper.vm.$nextTick()
        await flushPromises()

        expect(mockFindExecutions).toHaveBeenCalledTimes(1)
        const args = mockFindExecutions.mock.calls[0][0]
        expect(args["filters[parentId][EQUALS]"]).toBe("root-execution")
        expect(args["filters[kind][EQUALS]"]).toBe("LOOP")
        expect(args["filters[taskId][EQUALS]"]).toBe("per_region")
    })

    it("fetches a row's own task runs only when that row is expanded, not its siblings", async () => {
        mockFindExecutions.mockResolvedValue({
            results: [iterationExecution("it-1", "EMEA"), iterationExecution("it-2", "AMER")],
            total: 2,
        })
        mockExecutionApi.mockResolvedValue({id: "it-1", namespace: "company.team", flowId: "nested_loop_demo", taskRunList: []})

        const wrapper = mountTree()
        await wrapper.find('[data-test="loop-iteration-toggle"]').trigger("click")
        await flushPromises()

        expect(mockExecutionApi).not.toHaveBeenCalled()

        const rows = wrapper.findAll(".loop-iteration-row__main")
        expect(rows).toHaveLength(2)

        await rows[0].trigger("click")
        await flushPromises()

        expect(mockExecutionApi).toHaveBeenCalledTimes(1)
        expect(mockExecutionApi).toHaveBeenCalledWith({executionId: "it-1"})
    })

    it("shows an error with retry, and retry re-fetches", async () => {
        mockFindExecutions
            .mockRejectedValueOnce(new Error("network error"))
            .mockResolvedValueOnce({results: [iterationExecution("it-1", "EMEA")], total: 1})

        const wrapper = mountTree()
        await wrapper.find('[data-test="loop-iteration-toggle"]').trigger("click")
        await flushPromises()

        expect(wrapper.find(".ks-alert-stub").exists()).toBe(true)
        expect(wrapper.find('[data-test="loop-iteration-row"]').exists()).toBe(false)

        await wrapper.find(".ks-alert-stub button").trigger("click")
        await flushPromises()

        expect(mockFindExecutions).toHaveBeenCalledTimes(2)
        expect(wrapper.find(".ks-alert-stub").exists()).toBe(false)
        expect(wrapper.find('[data-test="loop-iteration-row"]').exists()).toBe(true)
    })

    it("expands a nested Loop inside an iteration's task runs", async () => {
        mockFindExecutions.mockImplementation(async (params: Record<string, unknown>) => {
            if (params["filters[taskId][EQUALS]"] === "per_region") {
                return {results: [iterationExecution("outer-1", "EMEA")], total: 1}
            }
            if (params["filters[taskId][EQUALS]"] === "per_quarter") {
                return {results: [iterationExecution("inner-1", "Q1")], total: 1}
            }
            return {results: [], total: 0}
        })
        mockExecutionApi.mockImplementation(async (params: any) => {
            const execId = params?.executionId;
            if (execId === "outer-1" || execId === "root-execution") {
                return {
                    id: "outer-1",
                    namespace: "company.team",
                    flowId: "nested_loop_demo",
                    taskRunList: [{id: "tr-per-quarter", taskId: "per_quarter", state: {current: "SUCCESS"}}],
                }
            }
            return {id: execId, namespace: "company.team", flowId: "nested_loop_demo", taskRunList: []}
        })
        mockFindTaskById.mockImplementation((_flow: unknown, taskId: string) =>
            taskId === "per_quarter" ? {type: "io.kestra.plugin.core.flow.Loop"} : {type: "io.kestra.plugin.core.flow.Commands"})

        const wrapper = mountTree()
        await wrapper.find('[data-test="loop-iteration-toggle"]').trigger("click")
        await flushPromises()

        const mainRow = wrapper.find(".loop-iteration-row__main")
        await mainRow.trigger("click")
        await wrapper.vm.$nextTick()
        await flushPromises()

        await vi.waitFor(() => {
            expect(wrapper.findAll(".loop-iteration-tree")).toHaveLength(2)
        })

        const nestedTree = wrapper.findAll(".loop-iteration-tree")[1]
        await nestedTree.find('[data-test="loop-iteration-toggle"]').trigger("click")
        await flushPromises()

        expect(mockFindExecutions).toHaveBeenCalledWith(expect.objectContaining({
            "filters[parentId][EQUALS]": "outer-1",
            "filters[kind][EQUALS]": "LOOP",
            "filters[taskId][EQUALS]": "per_quarter",
        }))
    })
})

async function flushPromises() {
    await new Promise((resolve) => setTimeout(resolve, 0))
    await new Promise((resolve) => setTimeout(resolve, 0))
}
