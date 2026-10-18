import {beforeEach, describe, expect, test, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import ChangeStatus from "./ChangeStatus.vue"
import {useExecutionsStore} from "../../stores/executions"
import {i18nMount} from "../../../tests/unit/i18nMount"

const toastSuccessMock = vi.fn()
const toastErrorMock = vi.fn()

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}))

vi.mock("../../utils/toast", () => ({
    useToast: () => ({
        success: (...args: unknown[]) => toastSuccessMock(...args),
        error: (...args: unknown[]) => toastErrorMock(...args),
    }),
}))

function mountChangeStatus(executionState: string, taskRunState: string) {
    return i18nMount(ChangeStatus, {
        props: {
            execution: {id: "execution-id", namespace: "io.kestra.tests", state: {current: executionState}},
            taskRun: {id: "task-run-id", taskId: "approval", state: {current: taskRunState}},
        },
        global: {
            plugins: [createPinia()],
            stubs: {
                KsButton: {template: "<button v-bind=\"$attrs\"><slot /></button>"},
                KsDialog: true,
            },
        },
    })
}

describe("ChangeStatus", () => {
    beforeEach(() => {
        toastSuccessMock.mockClear()
        toastErrorMock.mockClear()
    })

    test.each([
        ["PAUSED", "PAUSED"],
        ["KILLED", "KILLED"],
        ["RUNNING", "RUNNING"],
    ])("shouldDisableChangeStateWhenExecutionIs%s", (executionState, taskRunState) => {
        const wrapper = mountChangeStatus(executionState, taskRunState)

        expect(wrapper.find("button").attributes("disabled")).toBeDefined()
    })

    test("shouldEnableChangeStateWhenExecutionIsFailed", () => {
        const wrapper = mountChangeStatus("FAILED", "FAILED")

        expect(wrapper.find("button").attributes("disabled")).toBeUndefined()
    })

    test("reports error toast when changeStatus rejects", async () => {
        const pinia = createPinia()
        setActivePinia(pinia)
        const executionsStore = useExecutionsStore()
        executionsStore.changeStatus = vi.fn().mockRejectedValue(new Error("Failed to change state"))

        const wrapper = i18nMount(ChangeStatus, {
            props: {
                execution: {id: "execution-id", namespace: "io.kestra.tests", state: {current: "FAILED"}},
                taskRun: {id: "task-run-id", taskId: "approval", state: {current: "FAILED"}},
            },
            global: {
                plugins: [pinia],
                stubs: {
                    KsButton: {template: "<button v-bind=\"$attrs\"><slot /></button>"},
                    KsDialog: true,
                },
            },
        })

        await (wrapper.vm as unknown as {changeStatus: () => Promise<void>}).changeStatus()

        expect(toastErrorMock).toHaveBeenCalled()
        expect(toastSuccessMock).not.toHaveBeenCalled()
    })

    test("shows success toast when changeStatus succeeds", async () => {
        const pinia = createPinia()
        setActivePinia(pinia)
        const executionsStore = useExecutionsStore()
        executionsStore.changeStatus = vi.fn().mockResolvedValue({})
        executionsStore.waitForStateChange = vi.fn().mockResolvedValue({id: "execution-id"})
        executionsStore.followExecution = vi.fn()

        const wrapper = i18nMount(ChangeStatus, {
            props: {
                execution: {id: "execution-id", namespace: "io.kestra.tests", state: {current: "FAILED"}},
                taskRun: {id: "task-run-id", taskId: "approval", state: {current: "FAILED"}},
            },
            global: {
                plugins: [pinia],
                stubs: {
                    KsButton: {template: "<button v-bind=\"$attrs\"><slot /></button>"},
                    KsDialog: true,
                },
            },
        })

        await (wrapper.vm as unknown as {changeStatus: () => Promise<void>}).changeStatus()

        expect(toastSuccessMock).toHaveBeenCalled()
        expect(toastErrorMock).not.toHaveBeenCalled()
    })
})
