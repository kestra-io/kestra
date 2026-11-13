import {beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia, type Pinia} from "pinia"
import ChangeStatus from "./ChangeStatus.vue"
import {type Execution, useExecutionsStore} from "../../stores/executions"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {StateType} from "@kestra-io/kestra-sdk"

const isAllowedMock = vi.fn((..._args: unknown[]) => true)

const {toastSuccess, toastError} = vi.hoisted(() => ({
    toastSuccess: vi.fn(),
    toastError: vi.fn(),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: (...args: unknown[]) => isAllowedMock(...args)}}),
}))

vi.mock("../../utils/toast", () => ({
    useToast: () => ({success: toastSuccess, error: toastError}),
}))

const messages = {
    "change state": "Change state",
    "confirmation": "Confirmation",
    "change state confirm": "Change state of {task} on {id}",
    "change state confirm restart": "The task will be restarted",
    "change state current state": "Current state",
    "change state hint": {
        FAILED: ["The task failed"],
        SUCCESS: ["The task succeeded"],
        WARNING: ["The task warned"],
    },
    "mark as": "Mark as {status}",
    "ok": "OK",
    "cancel": "Cancel",
    "change state done": "State changed",
}

const globalConfig = {
    stubs: {
        KsButton: {template: "<button v-bind=\"$attrs\"><slot /></button>"},
        KsDialog: {
            props: ["modelValue", "id", "destroyOnClose", "appendToBody"],
            emits: ["update:modelValue"],
            template: "<div><slot name=\"header\" /><slot /><slot name=\"footer\" /></div>",
        },
        KsSelect: {
            name: "KsSelect",
            props: ["modelValue", "required", "teleported"],
            emits: ["update:modelValue"],
            template: "<div><slot name=\"label\" :value=\"modelValue\" /><slot /></div>",
        },
        KsOption: {props: ["value", "disabled"], template: "<div data-test=\"option\"><slot /></div>"},
        KsExecutionStatus: {props: ["status", "size", "label"], template: "<span>{{ status }}</span>"},
    },
}

function buildExecution(state: StateType): Execution {
    return {
        flowId: "flow-id",
        flowRevision: 1,
        id: "execution-id",
        namespace: "io.kestra.tests",
        state: {
            current: state,
            histories: [],
        },
    }
}

function buildTaskRun(state: StateType) {
    return {
        id: "task-run-id",
        flowId: "flow-id",
        namespace: "io.kestra.tests",
        executionId: "execution-id",
        taskId: "approval",
        state: {
            current: state,
            histories: [],
        },
    }
}

let pinia: Pinia

function mountChangeStatus(executionState: StateType, taskRunState: StateType) {
    return i18nMount(ChangeStatus, {
        messages,
        props: {
            execution: buildExecution(executionState),
            taskRun: buildTaskRun(taskRunState),
        },
        global: {
            plugins: [pinia],
            ...globalConfig,
        },
    })
}

async function openAndConfirm(wrapper: ReturnType<typeof mountChangeStatus>) {
    await wrapper.get("button").trigger("click")
    wrapper.findComponent({name: "KsSelect"}).vm.$emit("update:modelValue", "SUCCESS")
    const confirm = wrapper.findAll("button").find((button) => button.text() === "OK")
    expect(confirm).toBeDefined()
    await confirm!.trigger("click")
    await flushPromises()
}

describe("ChangeStatus", () => {
    beforeEach(() => {
        pinia = createPinia()
        setActivePinia(pinia)
        isAllowedMock.mockReturnValue(true)
        toastSuccess.mockClear()
        toastError.mockClear()
    })

    test.each([
        ["PAUSED", "PAUSED"],
        ["KILLED", "KILLED"],
        ["RUNNING", "RUNNING"],
    ] as const)("shouldDisableChangeStateWhenExecutionIs%s", (executionState, taskRunState) => {
        const wrapper = mountChangeStatus(executionState, taskRunState)

        expect(wrapper.find("button").attributes("disabled")).toBeDefined()
    })

    test("shouldEnableChangeStateWhenExecutionIsFailed", () => {
        const wrapper = mountChangeStatus("FAILED", "FAILED")

        expect(wrapper.find("button").attributes("disabled")).toBeUndefined()
    })

    test("shouldReportChangeStateFailureWhenRequestRejects", async () => {
        const wrapper = mountChangeStatus("FAILED", "FAILED")
        const store = useExecutionsStore()
        vi.spyOn(store, "changeStatus").mockRejectedValue(new Error("boom"))
        const followExecution = vi.spyOn(store, "followExecution").mockImplementation(() => undefined)

        await openAndConfirm(wrapper)

        expect(toastError).toHaveBeenCalledOnce()
        expect(toastSuccess).not.toHaveBeenCalled()
        expect(followExecution).toHaveBeenCalled()
    })

    test("shouldLeaveErrorReportingToGlobalToastWhenFailureIsAlreadyReported", async () => {
        const wrapper = mountChangeStatus("FAILED", "FAILED")
        const store = useExecutionsStore()
        vi.spyOn(store, "changeStatus").mockRejectedValue(Object.assign(new Error("conflict"), {
            status: 409,
            response: {data: {title: "Conflict"}},
        }))
        vi.spyOn(store, "followExecution").mockImplementation(() => undefined)

        await openAndConfirm(wrapper)

        expect(toastError).not.toHaveBeenCalled()
        expect(toastSuccess).not.toHaveBeenCalled()
    })

    test("shouldToastSuccessWhenChangeStateSucceeds", async () => {
        const wrapper = mountChangeStatus("FAILED", "FAILED")
        const store = useExecutionsStore()
        vi.spyOn(store, "changeStatus").mockResolvedValue(buildExecution("SUCCESS") as never)
        vi.spyOn(store, "waitForStateChange").mockResolvedValue(buildExecution("SUCCESS") as never)
        vi.spyOn(store, "followExecution").mockImplementation(() => undefined)

        await openAndConfirm(wrapper)

        expect(toastSuccess).toHaveBeenCalledOnce()
        expect(toastError).not.toHaveBeenCalled()
    })
})
