import {describe, expect, test, vi} from "vitest"
import {createPinia} from "pinia"
import ChangeStatus from "../../../../src/components/executions/ChangeStatus.vue"
import {i18nMount} from "../../i18nMount"

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}))

vi.mock("../../../../src/utils/toast", () => ({
    useToast: () => ({success: vi.fn(), error: vi.fn()}),
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
})
