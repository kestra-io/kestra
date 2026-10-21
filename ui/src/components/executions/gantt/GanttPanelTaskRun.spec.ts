import {afterAll, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {ref} from "vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import en from "../../../translations/en.json"
import type {Execution} from "../../../stores/executions"
import type {ExecutionTaskRun} from "../executionTaskRun"
import GanttPanelTaskRun from "./GanttPanelTaskRun.vue"
import {GANTT_TREE, createIterationNode, createTaskRunNode, iterationAncestors, type GanttTree, type TaskRunNode} from "./ganttTree"

const store = vi.hoisted(() => ({executions: {} as Record<string, unknown>}))
const mocks = vi.hoisted(() => ({flow: vi.fn()}))

vi.mock("../../../stores/executions", () => ({
    useExecutionsStore: () => store.executions,
}))
vi.mock("@kestra-io/kestra-sdk/flows", () => ({flow: mocks.flow}))

const ROOT_REVISION = 3
const ITERATION_REVISION = 7
const FLOW_SOURCE = "id: monthly_close\nnamespace: company.finance\ntasks:\n  - id: reconcile\n    type: io.kestra.plugin.core.execution.Fail\n"

function taskRun(id: string, taskId: string, state: string, start: string, extra: Partial<ExecutionTaskRun> = {}): ExecutionTaskRun {
    return {
        id,
        taskId,
        state: {current: state, histories: [{state, date: start}]},
        ...extra,
    } as ExecutionTaskRun
}

function execution(id: string, flowRevision: number, taskRunList: ExecutionTaskRun[]): Execution {
    return {id, namespace: "company.finance", flowId: "monthly_close", flowRevision, taskRunList} as unknown as Execution
}

const rootTaskRuns = [
    taskRun("root-extract", "extract", "SUCCESS", "2024-01-01T00:00:00Z"),
    taskRun("root-load", "load", "FAILED", "2024-01-01T00:00:05Z"),
]
const iterationTaskRuns = [
    taskRun("iter-extract", "extract", "SUCCESS", "2024-01-01T00:00:01Z"),
    taskRun("iter-reconcile", "reconcile", "FAILED", "2024-01-01T00:00:03Z"),
]

const rootExecution = execution("root-exec", ROOT_REVISION, rootTaskRuns)
const iterationExecution = execution("iter-exec", ITERATION_REVISION, iterationTaskRuns)

function rootNode(run: ExecutionTaskRun): TaskRunNode {
    return createTaskRunNode(rootExecution, run, undefined, [])
}

function iterationNode(run: ExecutionTaskRun): TaskRunNode {
    const loop = {parentExecutionId: "root-exec", taskId: "per_region", namespace: "company.finance", flowId: "monthly_close"}
    const row = {id: "iter-exec", value: "APAC", index: 2, state: {current: "FAILED", histories: []}}
    return createTaskRunNode(iterationExecution, run, undefined, iterationAncestors(createIterationNode(loop, row, [])))
}

function mountPanel(node: TaskRunNode) {
    const tree = {
        levelFilter: ref(undefined),
        executionEntry: vi.fn(() => ({execution: iterationExecution, loading: false})),
        loadExecution: vi.fn(),
        taskType: vi.fn(),
        select: vi.fn(),
    }
    const wrapper = i18nMount(GanttPanelTaskRun, {
        props: {node},
        locales: {en: en.en},
        global: {
            provide: {[GANTT_TREE as symbol]: tree as unknown as GanttTree},
            stubs: {
                GanttTaskLogs: true,
                TaskRunDetails: true,
                GanttPanelFailure: true,
                FailureAttempts: true,
                FailureStateHistory: true,
                FailureResolvedConfig: true,
                FailureExecutionInputs: true,
                FailureUpstreamOutputs: true,
                FailureStructuralImpact: true,
            },
        },
    })
    return {wrapper, tree}
}

async function openTab(wrapper: VueWrapper, name: string) {
    await wrapper.get(`#tab-task-${name}`).trigger("click")
    await flushPromises()
}

describe("GanttPanelTaskRun", () => {
    beforeEach(() => {
        store.executions = {execution: rootExecution, flow: undefined}
        mocks.flow.mockReset().mockResolvedValue({source: FLOW_SOURCE, inputs: [{id: "region"}]})
    })
    afterAll(() => localStorage.clear())

    it("shouldShowTheFailureSummaryOnlyForFailedTaskRuns", () => {
        const failed = mountPanel(rootNode(rootTaskRuns[1])).wrapper
        const succeeded = mountPanel(rootNode(rootTaskRuns[0])).wrapper

        expect(failed.findComponent({name: "GanttPanelFailure"}).exists()).toBe(true)
        expect(succeeded.findComponent({name: "GanttPanelFailure"}).exists()).toBe(false)
    })

    it("shouldShowTheFailureSummaryForAKilledTaskRun", () => {
        const killed = taskRun("root-killed", "load", "KILLED", "2024-01-01T00:00:05Z")

        const {wrapper} = mountPanel(rootNode(killed))

        expect(wrapper.findComponent({name: "GanttPanelFailure"}).exists()).toBe(true)
    })

    it("shouldNotLoadTheFlowNorMountTabContentUntilATabIsOpened", async () => {
        const {wrapper} = mountPanel(rootNode(rootTaskRuns[1]))
        await flushPromises()

        expect(mocks.flow).not.toHaveBeenCalled()
        expect(wrapper.findComponent({name: "FailureAttempts"}).exists()).toBe(false)
        expect(wrapper.findComponent({name: "FailureResolvedConfig"}).exists()).toBe(false)
        expect(wrapper.findComponent({name: "FailureStructuralImpact"}).exists()).toBe(false)
    })

    it("shouldNotLoadTheFlowForTabsThatDoNotNeedIt", async () => {
        const {wrapper} = mountPanel(rootNode(rootTaskRuns[1]))

        await openTab(wrapper, "attempts")

        expect(wrapper.findComponent({name: "FailureAttempts"}).exists()).toBe(true)
        expect(mocks.flow).not.toHaveBeenCalled()
    })

    it("shouldLoadTheFlowOnceWhenFlowDependentTabsAreOpened", async () => {
        const {wrapper} = mountPanel(rootNode(rootTaskRuns[1]))

        await openTab(wrapper, "configuration")
        await openTab(wrapper, "inputs-outputs")
        await openTab(wrapper, "structure")

        expect(mocks.flow).toHaveBeenCalledTimes(1)
        expect(mocks.flow).toHaveBeenCalledWith(expect.objectContaining({namespace: "company.finance", id: "monthly_close", revision: ROOT_REVISION, source: true}))
    })

    it("shouldResolveTheConfigurationAgainstTheRootExecutionForARootTaskRun", async () => {
        const {wrapper, tree} = mountPanel(rootNode(rootTaskRuns[1]))

        await openTab(wrapper, "configuration")

        const config = wrapper.getComponent({name: "FailureResolvedConfig"})
        expect(config.props("executionId")).toBe("root-exec")
        expect(config.props("taskRunId")).toBe("root-load")
        expect(tree.loadExecution).not.toHaveBeenCalled()
    })

    it("shouldTargetTheIterationExecutionForATaskRunInsideALoopIteration", async () => {
        const {wrapper, tree} = mountPanel(iterationNode(iterationTaskRuns[1]))

        await openTab(wrapper, "configuration")
        await openTab(wrapper, "inputs-outputs")

        expect(tree.loadExecution).toHaveBeenCalledWith("iter-exec")
        expect(mocks.flow).toHaveBeenCalledWith(expect.objectContaining({revision: ITERATION_REVISION}))
        const config = wrapper.getComponent({name: "FailureResolvedConfig"})
        expect(config.props("executionId")).toBe("iter-exec")
        expect(config.props("taskRunId")).toBe("iter-reconcile")
        expect(wrapper.getComponent({name: "FailureExecutionInputs"}).props("executionId")).toBe("iter-exec")
        expect(wrapper.getComponent({name: "FailureUpstreamOutputs"}).props("executionId")).toBe("iter-exec")
        expect(wrapper.getComponent({name: "GanttPanelFailure"}).props("execution")).toMatchObject({id: "iter-exec"})
    })

    it("shouldExtractTheTaskBlockAndInputIdsFromTheFlowSource", async () => {
        const {wrapper} = mountPanel(rootNode(taskRun("root-reconcile", "reconcile", "FAILED", "2024-01-01T00:00:05Z")))

        await openTab(wrapper, "configuration")
        await openTab(wrapper, "inputs-outputs")

        expect(wrapper.getComponent({name: "FailureResolvedConfig"}).props("rawBlock")).toContain("io.kestra.plugin.core.execution.Fail")
        expect(wrapper.getComponent({name: "FailureExecutionInputs"}).props("inputIds")).toEqual(["region"])
    })

    it("shouldOnlyOfferTaskRunsOfTheOwningExecutionInTheStructureTab", async () => {
        const {wrapper} = mountPanel(iterationNode(iterationTaskRuns[1]))

        await openTab(wrapper, "structure")

        const nodes = wrapper.getComponent({name: "FailureStructuralImpact"}).props("nodes") as Array<{taskRun: ExecutionTaskRun; relation: string}>
        expect(nodes.map(({taskRun: run, relation}) => [run.id, relation])).toEqual([
            ["iter-reconcile", "focused"],
            ["iter-extract", "sibling"],
        ])
    })

    it("shouldSkipTaskRunsThatNeverStartedInTheStructureTab", async () => {
        const neverStarted = {id: "iter-skipped", taskId: "skipped", state: {current: "CREATED", histories: []}} as unknown as ExecutionTaskRun
        iterationExecution.taskRunList = [...iterationTaskRuns, neverStarted]
        const {wrapper} = mountPanel(iterationNode(iterationTaskRuns[1]))

        await openTab(wrapper, "structure")

        const nodes = wrapper.getComponent({name: "FailureStructuralImpact"}).props("nodes") as Array<{taskRun: ExecutionTaskRun}>
        expect(nodes.map(({taskRun: run}) => run.id)).not.toContain("iter-skipped")
        iterationExecution.taskRunList = iterationTaskRuns
    })

    it("shouldSelectTheClickedNeighborInTheGanttKeepingItsIterationLineage", async () => {
        const {wrapper, tree} = mountPanel(iterationNode(iterationTaskRuns[1]))
        await openTab(wrapper, "structure")

        wrapper.getComponent({name: "FailureStructuralImpact"}).vm.$emit("focus", "iter-extract")

        expect(tree.select).toHaveBeenCalledTimes(1)
        const [selected, options] = tree.select.mock.calls[0] as unknown as [TaskRunNode, {reveal: boolean}]
        expect(selected).toMatchObject({kind: "taskRun", executionId: "iter-exec", key: "tr:iter-extract"})
        expect(selected.ancestors.map(ancestor => ancestor.key)).toEqual(["it:iter-exec"])
        expect(options).toEqual({reveal: true})
    })

    it("shouldKeepTheSelectedTabWhenTheSelectionMovesToAnotherTaskRun", async () => {
        const {wrapper} = mountPanel(rootNode(rootTaskRuns[1]))
        await openTab(wrapper, "configuration")

        await wrapper.setProps({node: rootNode(rootTaskRuns[0])})

        expect(wrapper.get("#tab-task-configuration").attributes("aria-selected")).toBe("true")
        expect(wrapper.getComponent({name: "FailureResolvedConfig"}).props("taskRunId")).toBe("root-extract")
    })
})
