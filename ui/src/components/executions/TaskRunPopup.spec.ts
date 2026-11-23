import {afterAll, afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {defineComponent, reactive, ref} from "vue"
import {setActivePinia} from "pinia"
import {designSystemI18nReady} from "@kestra-io/design-system"

const api = vi.hoisted(() => ({
    searchByExecution: vi.fn(),
    searchLogs: vi.fn(),
    loadTaskRunOutputs: vi.fn(),
}))

vi.mock("@kestra-io/kestra-sdk/metrics", () => ({searchByExecution: api.searchByExecution}))
vi.mock("@kestra-io/kestra-sdk/logs", () => ({searchLogs: api.searchLogs}))
vi.mock("../../composables/useTaskRunOutputs", async (importOriginal) => ({
    ...(await importOriginal<typeof import("../../composables/useTaskRunOutputs")>()),
    loadTaskRunOutputs: api.loadTaskRunOutputs,
}))
vi.mock("override/stores/auth", () => ({useAuthStore: () => ({user: {isAllowed: () => true}})}))
vi.mock("../../stores/plugins", () => ({usePluginsStore: () => ({icons: {}, loadIcon: vi.fn(), fetchIcons: vi.fn()})}))
vi.mock("../plugins/TaskIcon.vue", () => ({default: defineComponent({render: () => null})}))

import TaskRunPopup from "./TaskRunPopup.vue"
import {EXECUTION_ARTIFACTS_KEY} from "./composables/useExecutionArtifacts"
import {useExecutionsStore, type Execution} from "../../stores/executions"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {routedPinia} from "../../../tests/unit/memoryRouter"
import en from "../../translations/en.json"

const stubChild = (name: string, props: string[] = []) => defineComponent({name, props, render: () => null})

function taskRun(id: string, current: string, extra: Record<string, unknown> = {}) {
    const histories = [{date: "2026-01-01T00:00:00.000Z", state: "RUNNING"}, {date: "2026-01-01T00:00:02.000Z", state: current}]
    return {id, taskId: "dbt", state: {current, histories}, attempts: [{state: {current}}], ...extra}
}

function executionWith(taskRuns: ReturnType<typeof taskRun>[], executionState = "SUCCESS") {
    return {
        id: "exec-1",
        namespace: "company",
        flowId: "flow",
        flowRevision: 1,
        state: {current: executionState},
        taskRunList: taskRuns,
    } as unknown as Execution
}

function artifactsContext(artifacts: {key: string, taskId: string, title: string}[], manifestReady = true) {
    return {
        RemoteComponent: ref(undefined),
        artifacts: ref(artifacts.map((artifact) => ({...artifact, taskType: "graph", cls: "dbt.Cli"}))),
        tasks: ref([]),
        subtitleResolvers: reactive({}),
        manifestReady: ref(manifestReady),
        hasResolvedComponent: () => true,
        hasFailedComponent: () => false,
    }
}

interface MountOptions {
    execution?: Execution
    modelValue?: boolean
    initialTab?: "metrics" | "logs" | "outputs" | "artifacts"
    artifacts?: ReturnType<typeof artifactsContext>
    nodeActions?: {key: string, label: string, onClick: () => void}[]
}

const mounted: {setProps: (props: {modelValue: boolean}) => Promise<void>, unmount: () => void}[] = []

function mountPopup({execution = executionWith([taskRun("run-1", "SUCCESS")]), modelValue = true, initialTab, artifacts, nodeActions}: MountOptions = {}) {
    useExecutionsStore().execution = execution
    const wrapper = i18nMount(TaskRunPopup, {
        attachTo: document.body,
        locales: en,
        props: {
            modelValue,
            execution,
            taskId: "dbt",
            taskType: "io.kestra.plugin.dbt.cloud.TriggerRun",
            initialTab,
            nodeActions: nodeActions as never,
        },
        global: {
            stubs: {
                MetricsTable: stubChild("MetricsTable"),
                TaskRunLogs: stubChild("TaskRunLogs"),
                ExecutionVariableExplorer: stubChild("ExecutionVariableExplorer"),
                ExecutionArtifactViewer: stubChild("ExecutionArtifactViewer"),
                TaskRunActions: stubChild("TaskRunActions", ["nodeActions"]),
                Restart: stubChild("Restart"),
                ChangeStatus: stubChild("ChangeStatus"),
                TaskEdit: stubChild("TaskEdit"),
                WorkerInfo: stubChild("WorkerInfo"),
            },
            provide: artifacts ? {[EXECUTION_ARTIFACTS_KEY as symbol]: artifacts} : {},
        },
    })
    mounted.push(wrapper)
    return wrapper
}

const text = (selector: string) => document.body.querySelector(selector)?.textContent?.replace(/\s+/g, " ").trim()
const tabCount = (tab: string) => text(`[data-test='task-run-popup-tab-${tab}'] [data-test='tab-count']`)

afterAll(() => designSystemI18nReady())

describe("TaskRunPopup", () => {
    beforeEach(() => {
        setActivePinia(routedPinia())
        api.searchByExecution.mockResolvedValue({results: [], total: 5})
        api.searchLogs.mockResolvedValue({results: [], total: 14, type: "OFFSET"})
        api.loadTaskRunOutputs.mockResolvedValue({runId: 512994, manifest: "kestra:///manifest.json", runResults: "kestra:///run_results.json"})
    })

    afterEach(async () => {
        const wrappers = mounted.splice(0)
        await Promise.all(wrappers.map((wrapper) => wrapper.setProps({modelValue: false})))
        await flushPromises()
        wrappers.forEach((wrapper) => wrapper.unmount())
        document.body.innerHTML = ""
        document.body.className = ""
        localStorage.clear()
        vi.clearAllMocks()
    })

    it("shouldShowTaskIdStateTypeDurationAndAttemptInTheHeader", async () => {
        // Given
        const execution = executionWith([taskRun("run-1", "SUCCESS", {attempts: [{state: {current: "FAILED"}}, {state: {current: "SUCCESS"}}]})])

        // When
        mountPopup({execution})
        await flushPromises()

        // Then
        expect(text("[data-test='task-run-popup-title']")).toBe("dbt")
        expect(text("[data-test='task-run-popup-state']")).toBe("Success")
        expect(text("[data-test='task-run-popup-type']")).toBe("io.kestra.plugin.dbt.cloud.TriggerRun")
        expect(text("[data-test='task-run-popup-duration']")).toContain("2.00s")
        expect(text("[data-test='task-run-popup-attempt']")).toBe("Attempt 2")
    })

    it("shouldShowTheLiveDurationOfARunningTaskRunInTheHeader", async () => {
        // Given
        const histories = [{date: new Date(Date.now() - 5_000).toISOString(), state: "RUNNING"}]
        const execution = executionWith([taskRun("run-1", "RUNNING", {state: {current: "RUNNING", histories}})], "RUNNING")

        // When
        mountPopup({execution})
        await flushPromises()

        // Then
        expect(document.body.querySelector("[data-test='task-run-popup-duration'] [data-test='duration-value']")).not.toBeNull()
    })

    it("shouldNotRenderAnyActionButtonDirectlyBesideAnother", async () => {
        // When
        mountPopup()
        await flushPromises()

        // Then
        const actions = document.body.querySelector(".popup-actions")!
        expect([...actions.querySelectorAll("button + button")]).toHaveLength(0)
    })

    it("shouldDisableInterruptWithoutHidingItWhenTheTaskRunIsNotRunning", async () => {
        // Given
        mountPopup({execution: executionWith([taskRun("run-1", "SUCCESS")])})
        await flushPromises()

        // When
        const interrupt = [...document.body.querySelectorAll("button")].find((button) => button.textContent?.trim() === "Interrupt")

        // Then
        expect(interrupt).toBeDefined()
        expect(interrupt!.disabled).toBe(true)
    })

    it("shouldEnableInterruptWhenTheTaskRunIsRunning", async () => {
        // Given
        mountPopup({execution: executionWith([taskRun("run-1", "RUNNING")], "RUNNING")})
        await flushPromises()

        // When
        const interrupt = [...document.body.querySelectorAll("button")].find((button) => button.textContent?.trim() === "Interrupt")

        // Then
        expect(interrupt!.disabled).toBe(false)
    })

    it("shouldShowACounterOnEachTab", async () => {
        // Given
        const artifacts = artifactsContext([
            {key: "dbt:graph", taskId: "dbt", title: "Graph"},
            {key: "dbt:docs", taskId: "dbt", title: "Docs"},
            {key: "other:graph", taskId: "other", title: "Other"},
        ])

        // When
        mountPopup({artifacts})
        await flushPromises()

        // Then
        expect(tabCount("metrics")).toBe("5")
        expect(tabCount("logs")).toBe("14")
        expect(tabCount("outputs")).toBe("3")
        expect(tabCount("artifacts")).toBe("2")
    })

    it("shouldQueryTheCountersOfTheSelectedTaskRunOnly", async () => {
        // When
        mountPopup()
        await flushPromises()

        // Then
        expect(api.searchByExecution).toHaveBeenCalledWith(expect.objectContaining({executionId: "exec-1", taskId: "dbt", taskRunId: "run-1", page: 1, size: 1}))
        expect(api.searchLogs).toHaveBeenCalledWith(expect.objectContaining({
            filters: expect.arrayContaining([
                expect.objectContaining({field: "taskRunId", value: "run-1"}),
                expect.objectContaining({field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: "INFO"}),
                expect.objectContaining({field: "attemptNumber", value: "0"}),
            ]),
            size: 1,
        }))
        expect(api.loadTaskRunOutputs).toHaveBeenCalledWith("exec-1", "run-1")
    })

    it("shouldCountTheLogsOfManyChildRunsWithABoundedNumberOfRequests", async () => {
        // Given
        const children = Array.from({length: 50}, (_, index) => taskRun(`child-${index}`, "SUCCESS", {parentTaskRunId: "run-1"}))
        const execution = executionWith([taskRun("run-1", "SUCCESS"), ...children])

        // When
        mountPopup({execution})
        await flushPromises()

        // Then
        expect(api.searchLogs).toHaveBeenCalledTimes(2)
        const childRequest = api.searchLogs.mock.calls.map(([params]) => params.filters).find((filters: {operation: string}[]) => filters.some((filter) => filter.operation === "IN"))
        expect(childRequest.find((filter: {operation: string}) => filter.operation === "IN").value).toHaveLength(20)
    })

    it("shouldNotRefetchTheLogsCountWhenAChildRunAppears", async () => {
        // Given
        const execution = reactive(executionWith([taskRun("run-1", "RUNNING")], "RUNNING"))
        mountPopup({execution})
        await flushPromises()
        api.searchLogs.mockClear()

        // When
        execution.taskRunList!.push(taskRun("child-1", "RUNNING", {parentTaskRunId: "run-1"}) as never)
        await flushPromises()

        // Then
        expect(api.searchLogs).not.toHaveBeenCalled()
    })

    it("shouldAlwaysMountTheLogsViewerEvenWhenNoLogMatchesTheDefaultLevel", async () => {
        // Given
        api.searchLogs.mockResolvedValue({results: [], total: 0, type: "OFFSET"})

        // When
        const wrapper = mountPopup({initialTab: "logs"})
        await flushPromises()

        // Then
        expect(wrapper.findComponent({name: "TaskRunLogs"}).exists()).toBe(true)
    })

    it("shouldNotFetchAnythingWhileClosed", async () => {
        // When
        mountPopup({modelValue: false})
        await flushPromises()

        // Then
        expect(api.searchByExecution).not.toHaveBeenCalled()
        expect(api.searchLogs).not.toHaveBeenCalled()
        expect(api.loadTaskRunOutputs).not.toHaveBeenCalled()
    })

    it("shouldFlagACounterThatFailedToLoadWithoutBreakingTheOthers", async () => {
        // Given
        const consoleError = vi.spyOn(console, "error").mockImplementation(() => {})
        api.searchByExecution.mockRejectedValue(new Error("boom"))

        // When
        mountPopup()
        await flushPromises()

        // Then
        expect(document.body.querySelector("[data-test='task-run-popup-tab-metrics'] [data-test='tab-count-error']")).not.toBeNull()
        expect(tabCount("logs")).toBe("14")
        expect(consoleError).toHaveBeenCalled()
        consoleError.mockRestore()
    })

    it("shouldShowALoadingPlaceholderInPlaceOfACounterWhileItLoads", async () => {
        // Given
        api.searchByExecution.mockReturnValue(new Promise(() => {}))

        // When
        mountPopup()
        await flushPromises()

        // Then
        expect(document.body.querySelector("[data-test='task-run-popup-tab-metrics'] [data-test='tab-count-loading']")).not.toBeNull()
    })

    it("shouldOpenOnTheRequestedTab", async () => {
        // When
        mountPopup({initialTab: "artifacts", artifacts: artifactsContext([{key: "dbt:graph", taskId: "dbt", title: "Graph"}])})
        await flushPromises()

        // Then
        expect(document.body.querySelector("[role='tab'][aria-selected='true']")?.textContent).toContain("Artifacts")
        expect(document.body.querySelector("[data-test='task-run-popup-artifacts-empty']")).toBeNull()
    })

    it("shouldShowAnEmptyStateWhenTheTaskHasNoArtifact", async () => {
        // When
        mountPopup({initialTab: "artifacts", artifacts: artifactsContext([{key: "other:graph", taskId: "other", title: "Other"}])})
        await flushPromises()

        // Then
        expect(text("[data-test='task-run-popup-artifacts-empty']")).toContain("This task does not provide an artifact.")
        expect(tabCount("artifacts")).toBe("0")
    })

    it("shouldShowAnEmptyStateWhenNoArtifactModuleIsProvided", async () => {
        // When
        mountPopup({initialTab: "artifacts"})
        await flushPromises()

        // Then
        expect(document.body.querySelector("[data-test='task-run-popup-artifacts-empty']")).not.toBeNull()
    })

    it("shouldOfferARunPickerOnlyWhenTheTaskRanMoreThanOnce", async () => {
        // When
        const single = mountPopup()
        await flushPromises()

        // Then
        expect(document.body.querySelector("[data-test='task-run-popup-picker']")).toBeNull()
        await single.setProps({modelValue: false})
        single.unmount()

        // When
        mountPopup({execution: executionWith([taskRun("run-1", "SUCCESS"), taskRun("run-2", "FAILED")])})
        await flushPromises()

        // Then
        expect(text("[data-test='task-run-popup-picker']")).toBe("Iteration 1")
    })

    it("shouldFollowTheTaskRunSelectedInThePicker", async () => {
        // Given
        const execution = executionWith([taskRun("run-1", "SUCCESS"), taskRun("run-2", "FAILED")])
        mountPopup({execution})
        await flushPromises()
        api.searchByExecution.mockClear()

        // When
        useExecutionsStore().taskRunSelections.set("exec-1|dbt", "run-2")
        await flushPromises()

        // Then
        expect(text("[data-test='task-run-popup-state']")).toBe("Failed")
        expect(text("[data-test='task-run-popup-picker']")).toBe("Iteration 2")
        expect(api.searchByExecution).toHaveBeenCalledWith(expect.objectContaining({taskRunId: "run-2"}))
    })

    it("shouldCloseWithoutLeavingAnArtifactInTheUrl", async () => {
        // Given
        const wrapper = mountPopup({artifacts: artifactsContext([{key: "dbt:graph", taskId: "dbt", title: "Graph"}])})
        await flushPromises()
        const router = wrapper.vm.$router

        // When
        ;(document.body.querySelector("[data-test='task-run-popup-close']") as HTMLElement).click()
        await flushPromises()

        // Then
        expect(wrapper.emitted("update:modelValue")).toEqual([[false]])
        expect(router.currentRoute.value.query.artifact).toBeUndefined()
    })

    it("shouldCloseBeforeRunningAnyNodeAction", async () => {
        // Given
        const expand = vi.fn()
        const wrapper = mountPopup({nodeActions: [{key: "show-details", label: "Details", onClick: expand}]})
        await flushPromises()
        const actions = wrapper.findComponent({name: "TaskRunActions"}).props("nodeActions") as {key: string, onClick: () => void}[]

        // When
        actions[0].onClick()

        // Then
        expect(wrapper.emitted("update:modelValue")).toEqual([[false]])
        expect(expand).toHaveBeenCalledOnce()
    })
})
