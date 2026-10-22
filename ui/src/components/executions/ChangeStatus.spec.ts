import {describe, expect, test, vi} from "vitest"
import {createPinia} from "pinia"
import ChangeStatus from "../../../src/components/executions/ChangeStatus.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {memoryRouter} from "../../../tests/unit/memoryRouter"
import {StateType} from "@kestra-io/kestra-sdk"

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}))

vi.mock("../../../../src/utils/toast", () => ({
    useToast: () => ({success: vi.fn(), error: vi.fn()}),
}))

function mountChangeStatus(executionState: StateType, taskRunState: StateType) {
    return i18nMount(ChangeStatus, {
        props: {
            execution: {
                flowId: "flow-id",
                flowRevision: 1,
                id: "execution-id", 
                namespace: "io.kestra.tests", 
                state: {
                    current: executionState, 
                    histories: [],
                }},
            taskRun: {
                id: "task-run-id", 
                flowId: "flow-id",
                namespace: "io.kestra.tests",
                executionId: "execution-id",
                taskId: "approval", 
                state: {
                    current: taskRunState, 
                    histories: [],
                }},
        },
        global: {
            plugins: [createPinia(), memoryRouter()],
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
    ] as const)("shouldDisableChangeStateWhenExecutionIs%s", (executionState, taskRunState) => {
        const wrapper = mountChangeStatus(executionState, taskRunState)

        expect(wrapper.find("button").attributes("disabled")).toBeDefined()
    })

    test("shouldEnableChangeStateWhenExecutionIsFailed", () => {
        const wrapper = mountChangeStatus("FAILED", "FAILED")

        expect(wrapper.find("button").attributes("disabled")).toBeUndefined()
    })
})
