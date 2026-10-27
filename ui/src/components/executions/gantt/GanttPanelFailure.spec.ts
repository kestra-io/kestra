import {afterAll, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import en from "../../../translations/en.json"
import type {Execution} from "../../../stores/executions"
import type {ExecutionTaskRun} from "../executionTaskRun"
import GanttPanelFailure from "./GanttPanelFailure.vue"
import {SUBFLOW_TYPE, createTaskRunNode, type TaskRunNode} from "./ganttTree"

const store = vi.hoisted(() => ({executions: {} as Record<string, unknown>}))
const mocks = vi.hoisted(() => ({
    loadTaskRunOutputs: vi.fn(),
    promptCopilot: vi.fn(),
}))

vi.mock("../../../stores/executions", () => ({
    useExecutionsStore: () => store.executions,
}))
vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {hasAny: () => true, isAllowed: () => true}}),
}))
vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({promptCopilot: mocks.promptCopilot}),
}))
vi.mock("vue-router", () => ({
    useRoute: () => ({params: {tenant: "main"}, query: {}}),
    useRouter: () => ({push: vi.fn()}),
}))
vi.mock("../../../composables/useTaskRunOutputs", () => ({
    loadTaskRunOutputs: mocks.loadTaskRunOutputs,
}))

const failedRun = {
    id: "tr-reconcile",
    taskId: "reconcile",
    attempts: [{state: {current: "FAILED", histories: []}}, {state: {current: "FAILED", histories: []}}],
    state: {current: "FAILED", histories: [{state: "FAILED", date: "2024-01-01T00:00:00Z"}]},
} as unknown as ExecutionTaskRun

const iterationExecution = {id: "iter-exec", namespace: "company.finance", flowId: "monthly_close", flowRevision: 1} as unknown as Execution

function node(taskType?: string): TaskRunNode {
    return createTaskRunNode(iterationExecution, failedRun, taskType, [])
}

const KsButtonStub = {
    name: "KsButtonStub",
    props: ["disabled", "to"],
    template: "<button :disabled=\"disabled\"><slot /></button>",
}

function mountFailure(taskType?: string) {
    return i18nMount(GanttPanelFailure, {
        props: {node: node(taskType), execution: iterationExecution},
        locales: {en: en.en},
        global: {
            stubs: {
                Restart: true,
                SubFlowLink: true,
                RouterLink: {template: "<a><slot /></a>"},
                KsButton: KsButtonStub,
            },
        },
    })
}

function buttonByLabel(wrapper: ReturnType<typeof mountFailure>, label: string) {
    return wrapper.findAll("button").find(button => button.text().trim() === label)
}

describe("GanttPanelFailure", () => {
    beforeEach(() => {
        store.executions = {loadLogs: vi.fn().mockResolvedValue([])}
        mocks.loadTaskRunOutputs.mockReset().mockResolvedValue({})
        mocks.promptCopilot.mockReset()
    })
    afterAll(() => localStorage.clear())

    it("shouldSurfaceTheErrorMessageWithoutTheRestOfTheLogStream", async () => {
        store.executions = {
            loadLogs: vi.fn().mockResolvedValue([
                {level: "ERROR", message: "Ledger APAC Q3 is off by 1,204.50 EUR"},
            ]),
        }

        const wrapper = mountFailure()
        await flushPromises()

        expect(wrapper.get(".failure-error-summary__text").text()).toBe("Ledger APAC Q3 is off by 1,204.50 EUR")
    })

    it("shouldFallBackToTheLastLogMessageWhenNoErrorLevelLogExists", async () => {
        store.executions = {
            loadLogs: vi.fn().mockResolvedValue([
                {level: "INFO", message: "starting"},
                {level: "WARN", message: "retrying"},
            ]),
        }

        const wrapper = mountFailure()
        await flushPromises()

        expect(wrapper.get(".failure-error-summary__text").text()).toBe("retrying")
    })

    it("shouldShowASubflowErrorsExecutionLinkAsThePlainExecutionId", async () => {
        store.executions = {
            loadLogs: vi.fn().mockResolvedValue([
                {level: "ERROR", message: "Subflow execution [[link execution=\"child-1\" flowId=\"ledger\" namespace=\"company.team\"]] ends in FAILED state"},
            ]),
        }

        const wrapper = mountFailure()
        await flushPromises()

        expect(wrapper.get(".failure-error-summary__text").text()).toBe("Subflow execution child-1 ends in FAILED state")
    })

    it("shouldFetchTheErrorFromTheOwningExecutionScopedToTheTaskRun", async () => {
        mountFailure()
        await flushPromises()

        const loadLogs = store.executions.loadLogs as ReturnType<typeof vi.fn>
        expect(loadLogs).toHaveBeenCalledTimes(1)
        expect(loadLogs.mock.calls[0][0].executionId).toBe("iter-exec")
        const filters = loadLogs.mock.calls[0][0].filters
        expect(filters).toContainEqual({field: "taskRunId", operation: "EQUALS", value: "tr-reconcile"})
        expect(filters).toContainEqual({field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: "ERROR"})
    })

    it("shouldScopeReplayToTheTaskRunAndItsOwningExecution", () => {
        const wrapper = mountFailure()

        const restart = wrapper.getComponent({name: "Restart"})
        expect(restart.props("isReplay")).toBe(true)
        expect(restart.props("execution")).toMatchObject({id: "iter-exec"})
        expect(restart.props("taskRun")).toMatchObject({id: "tr-reconcile"})
        expect(restart.props("attemptIndex")).toBe(1)
    })

    it("shouldDeepLinkEditFlowToTheFailedTaskOfTheOwningFlow", () => {
        const wrapper = mountFailure()

        const edit = wrapper.findAllComponents(KsButtonStub).find(button => button.attributes("data-test") === "gantt-failure-edit-flow")!
        expect(edit.props("to")).toEqual({
            name: "flows/update/edit",
            params: {namespace: "company.finance", id: "monthly_close", tenant: "main"},
            query: {editTask: "reconcile"},
        })
    })

    it("shouldDisableAskingCopilotWhenNoErrorTextWasRecorded", async () => {
        const wrapper = mountFailure()
        await flushPromises()

        expect(buttonByLabel(wrapper, "Fix with AI")?.attributes("disabled")).toBeDefined()
    })

    it("shouldScopeTheCopilotPromptToTheErrorOfTheTaskRun", async () => {
        store.executions = {loadLogs: vi.fn().mockResolvedValue([{level: "ERROR", message: "boom"}])}
        const wrapper = mountFailure()
        await flushPromises()

        await buttonByLabel(wrapper, "Fix with AI")?.trigger("click")

        expect(mocks.promptCopilot).toHaveBeenCalledWith(
            "Fix the task reconcile as it generated the following error:\nboom",
            expect.objectContaining({newThread: true}),
        )
    })

    it("shouldOfferTheChildExecutionOfAFailedSubflowTaskRun", async () => {
        mocks.loadTaskRunOutputs.mockResolvedValue({executionId: "child-exec-9"})

        const wrapper = mountFailure(SUBFLOW_TYPE)
        await flushPromises()

        expect(mocks.loadTaskRunOutputs).toHaveBeenCalledWith("iter-exec", "tr-reconcile")
        expect(wrapper.getComponent({name: "SubFlowLink"}).props("executionId")).toBe("child-exec-9")
    })

    it("shouldNotLookForAChildExecutionWhenTheTaskIsNotASubflow", async () => {
        const wrapper = mountFailure("io.kestra.plugin.core.log.Log")
        await flushPromises()

        expect(mocks.loadTaskRunOutputs).not.toHaveBeenCalled()
        expect(wrapper.findComponent({name: "SubFlowLink"}).exists()).toBe(false)
    })
})
