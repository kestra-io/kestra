import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {ref} from "vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {useExecutionsStore} from "../../stores/executions"
import LoopIterationTree from "./LoopIterationTree.vue"
import type {ExecutionTaskRun} from "./executionTaskRun"
import {GANTT_TREE, LOOP_TYPE, createGanttTree, createTaskRunNode, type GanttTree} from "./gantt/ganttTree"

const mockExecution = vi.hoisted(() => vi.fn())
vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    execution: (...args: unknown[]) => mockExecution(...args),
}))

const histories = [
    {state: "RUNNING", date: "2026-01-01T00:00:00Z"},
    {state: "SUCCESS", date: "2026-01-01T00:00:01Z"},
]

const iterationExecution = (id: string, value: string, index: number, state = "SUCCESS") => ({
    id,
    state: {current: state, histories},
    loopRun: {taskId: "per_region", taskRunId: "tr-region", index, value, parents: []},
})

const loopTaskRun = {id: "tr-region", taskId: "per_region", state: {current: "SUCCESS", histories}} as unknown as ExecutionTaskRun
const loopNode = createTaskRunNode({id: "root-execution", namespace: "company.team", flowId: "nested_loop_demo"}, loopTaskRun, LOOP_TYPE, [])

let tree: GanttTree
let findExecutions: ReturnType<typeof vi.spyOn>

function pageOf(total: number, size = 10) {
    return async (options: Record<string, unknown>) => {
        if (options.onlyTotal) return total
        const page = (options.page as number) ?? 1
        const start = (page - 1) * size
        const results = Array.from({length: Math.max(0, Math.min(size, total - start))}, (_, i) => iterationExecution(`it-${start + i}`, `REGION-${start + i}`, start + i))
        return {results, total}
    }
}

function mountTree() {
    return i18nMount(LoopIterationTree, {
        props: {node: loopNode},
        global: {
            provide: {[GANTT_TREE as symbol]: tree},
            stubs: {
                SubFlowLink: true,
                TaskRunActions: true,
                TaskIcon: true,
                Duration: true,
                RouterLink: {template: "<a><slot /></a>"},
            },
        },
    })
}

beforeEach(() => {
    setActivePinia(createPinia())
    mockExecution.mockReset()
    findExecutions = vi.spyOn(useExecutionsStore(), "findExecutions")
    tree = createGanttTree({compact: ref(false), levelFilter: ref(undefined)})
})

describe("LoopIterationTree", () => {
    it("shouldFetchFirstPageScopedToLoopWhenMounted", async () => {
        findExecutions.mockImplementation(pageOf(3) as never)

        const wrapper = mountTree()
        await flushPromises()

        expect(findExecutions).toHaveBeenCalledWith(expect.objectContaining({
            "filters[parentId][EQUALS]": "root-execution",
            "filters[kind][EQUALS]": "LOOP",
            "filters[taskId][EQUALS]": "per_region",
            page: 1,
        }))
        expect(wrapper.findAll("[data-test='loop-iteration-row']")).toHaveLength(3)
        expect(wrapper.find("[data-test='loop-preview-footer']").exists()).toBe(false)
    })

    it("shouldFetchIterationTaskRunsOnlyForTheRowWhoseChevronIsToggled", async () => {
        findExecutions.mockImplementation(pageOf(2) as never)
        mockExecution.mockResolvedValue({
            id: "it-0",
            taskRunList: [{id: "tr-extract", taskId: "extract", state: {current: "SUCCESS", histories}}],
        })
        const wrapper = mountTree()
        await flushPromises()
        expect(mockExecution).not.toHaveBeenCalled()

        await wrapper.findAll("[data-test='gantt-row-chevron']")[0].trigger("click")
        await flushPromises()

        expect(mockExecution).toHaveBeenCalledTimes(1)
        expect(mockExecution).toHaveBeenCalledWith({executionId: "it-0"})
        expect(wrapper.findAll("[data-test='iteration-task-run']")).toHaveLength(1)
    })

    it("shouldSelectIterationRowWithoutTogglingItWhenRowIsClicked", async () => {
        findExecutions.mockImplementation(pageOf(2) as never)
        const wrapper = mountTree()
        await flushPromises()

        await wrapper.findAll("[data-test='loop-iteration-row']")[1].trigger("click")

        expect(tree.selected.value?.key).toBe("it:it-1")
        expect(tree.isExpanded("it:it-1")).toBe(false)
        expect(mockExecution).not.toHaveBeenCalled()
    })

    it("shouldShowErrorWithRetryThatRefetchesWhenFirstPageFails", async () => {
        findExecutions
            .mockRejectedValueOnce(new Error("network error"))
            .mockImplementation(pageOf(1) as never)
        const wrapper = mountTree()
        await flushPromises()
        expect(wrapper.find("[data-test='loop-iteration-row']").exists()).toBe(false)

        await wrapper.findComponent({name: "KsAlert"}).findComponent({name: "KsButton"}).trigger("click")
        await flushPromises()

        expect(wrapper.find("[data-test='loop-iteration-row']").exists()).toBe(true)
    })

    it("shouldLoadNextPageWhenLoadMoreIsClicked", async () => {
        findExecutions.mockImplementation(pageOf(12) as never)
        const wrapper = mountTree()
        await flushPromises()
        expect(wrapper.findAll("[data-test='loop-iteration-row']")).toHaveLength(10)
        expect(wrapper.get("[data-test='loop-preview-footer']").text()).toContain("load_10_more")

        await wrapper.get("[data-test='loop-load-more']").trigger("click")
        await flushPromises()

        expect(wrapper.findAll("[data-test='loop-iteration-row']")).toHaveLength(12)
        expect(wrapper.find("[data-test='loop-load-more']").exists()).toBe(false)
    })

    it("shouldOnlyListFailedIterationsWhenGlobalFailedOnlyIsOn", async () => {
        findExecutions.mockImplementation((async (options: Record<string, unknown>) => {
            if (options.onlyTotal) return 2
            const failedOnly = options["filters[state][IN]"] !== undefined
            return {
                results: failedOnly
                    ? [iterationExecution("it-1", "AMER", 1, "FAILED")]
                    : [iterationExecution("it-0", "EMEA", 0), iterationExecution("it-1", "AMER", 1, "FAILED")],
                total: failedOnly ? 1 : 2,
            }
        }) as never)
        const wrapper = mountTree()
        await flushPromises()
        expect(wrapper.findAll("[data-test='loop-iteration-row']")).toHaveLength(2)

        tree.failedOnly.value = true
        await flushPromises()

        expect(wrapper.findAll("[data-test='loop-iteration-row']")).toHaveLength(1)
    })
})
