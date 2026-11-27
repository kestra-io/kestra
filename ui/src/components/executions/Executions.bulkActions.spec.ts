import {defineComponent, h, ref} from "vue"
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {setActivePinia} from "pinia"
import {flushPromises} from "@vue/test-utils"

import {i18nShallowMount} from "../../../tests/unit/i18nMount"
import {routedPinia} from "../../../tests/unit/memoryRouter"
import {useExecutionsStore} from "../../stores/executions"
import Executions from "./Executions.vue"

const {toastConfirm, toastSuccess, toastRequestError, toastError, confirmExecutionDeletion} = vi.hoisted(() => ({
    toastConfirm: vi.fn((_message: unknown, callback: () => unknown) => Promise.resolve(callback())),
    toastSuccess: vi.fn(),
    toastRequestError: vi.fn(),
    toastError: vi.fn(),
    confirmExecutionDeletion: vi.fn().mockResolvedValue({deleteLogs: false, deleteMetrics: false, deleteStorage: false}),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({
        user: {
            isAllowed: () => true,
            hasAnyAction: () => true,
            hasAnyActionOnAnyNamespace: () => true,
            hasAny: () => true,
        },
    }),
}))

vi.mock("../../utils/toast", async (importOriginal) => {
    const actual = await importOriginal<typeof import("../../utils/toast")>()
    return {
        ...actual,
        useToast: () => ({
            confirm: toastConfirm,
            success: toastSuccess,
            requestError: toastRequestError,
            error: toastError,
        }),
    }
})

vi.mock("./composables/useExecutionDeletionDialog", () => ({
    useExecutionDeletionDialog: () => ({confirmExecutionDeletion}),
}))

const tableControls = {
    toggleAllUnselected: vi.fn(),
    reload: vi.fn(),
    resetAndReload: vi.fn(),
}

const KsDataTable = defineComponent({
    name: "KsDataTable",
    setup(_, {slots, expose}) {
        const queryBulkAction = ref(true)
        const selection = ref<string[]>([])
        expose({
            queryBulkAction,
            selection,
            toggleAllUnselected: tableControls.toggleAllUnselected,
            reload: tableControls.reload,
            resetAndReload: tableControls.resetAndReload,
        })
        return () => h("div", [slots["bulk-actions"]?.()])
    },
})

const KsDialog = defineComponent({
    name: "KsDialog",
    props: ["modelValue"],
    template: "<div v-if=\"modelValue\" data-test=\"dialog\"><slot name=\"header\" /><slot /><slot name=\"footer\" /></div>",
})

const KsButton = defineComponent({
    name: "KsButton",
    emits: ["click"],
    template: "<button v-bind=\"$attrs\" @click=\"$emit('click', $event)\"><slot /></button>",
})

const KsTooltip = defineComponent({
    name: "KsTooltip",
    template: "<div><slot /></div>",
})

function mountExecutions() {
    return i18nShallowMount(Executions, {
        props: {topbar: false, filter: false, visibleCharts: false},
        global: {
            plugins: [routedPinia()],
            stubs: {
                KsDataTable,
                KsDialog,
                KsButton,
                KsTooltip,
                KsSelect: {template: "<div><slot /></div>"},
                KsOption: {template: "<div><slot /></div>"},
                KsExecutionStatus: true,
                KsTableColumn: true,
                KsDropdown: true,
                KsDropdownMenu: true,
                KsDropdownItem: true,
            },
        },
    })
}

const BULK_PROBLEM = {
    type: "https://kestra.io/problems/bulk-validation-failed",
    title: "Bulk validation failed",
    status: 400,
    detail: "Bulk action rejected",
    errors: [{detail: "execution cannot be updated", path: "exec-1"}],
}

function problemError(status: number, problem: object) {
    return Object.assign(new Error(`Request failed with ${status}`), {
        status,
        problem,
        response: {data: problem, status},
    })
}

describe("Executions query-mode bulk actions", () => {
    let originalTitle: string

    beforeEach(() => {
        originalTitle = document.title
        setActivePinia(routedPinia())
        vi.clearAllMocks()
    })

    afterEach(() => {
        vi.useRealTimers()
        document.title = originalTitle
    })

    it("reports problem details and keeps selection when a query-mode bulk action fails", async () => {
        const wrapper = mountExecutions()
        const executionsStore = useExecutionsStore()
        const error = problemError(400, BULK_PROBLEM)
        const queryKill = vi.spyOn(executionsStore, "queryKill").mockRejectedValue(error)

        const killButton = wrapper.findAll("button").find((button) => button.text().trim() === "kill")
        expect(killButton).toBeDefined()
        await killButton!.trigger("click")
        await flushPromises()

        expect(queryKill).toHaveBeenCalledOnce()
        expect(toastRequestError).toHaveBeenCalledWith(error, [{message: "execution cannot be updated"}], "Bulk validation failed")
        expect(toastSuccess).not.toHaveBeenCalled()
        expect(tableControls.toggleAllUnselected).not.toHaveBeenCalled()
        expect(tableControls.reload).not.toHaveBeenCalled()
    })

    it("reports network failures with no response body when deleting by query", async () => {
        const wrapper = mountExecutions()
        const executionsStore = useExecutionsStore()
        const error = new Error("Network Error")
        const queryDelete = vi.spyOn(executionsStore, "queryDeleteExecution").mockRejectedValue(error)

        const deleteButton = wrapper.findAll("button").find((button) => button.text().trim() === "delete")
        expect(deleteButton).toBeDefined()
        await deleteButton!.trigger("click")
        await flushPromises()

        expect(queryDelete).toHaveBeenCalledOnce()
        expect(toastRequestError).toHaveBeenCalledWith(error, "errors.generic.content", "errors.generic.title")
        expect(toastSuccess).not.toHaveBeenCalled()
        expect(tableControls.toggleAllUnselected).not.toHaveBeenCalled()
        expect(tableControls.reload).not.toHaveBeenCalled()
    })

    it("reports query-mode restart failures without clearing selection", async () => {
        const wrapper = mountExecutions()
        const executionsStore = useExecutionsStore()
        const error = new Error("Network Error")
        const queryRestart = vi.spyOn(executionsStore, "queryRestartExecution").mockRejectedValue(error)

        const restartButton = wrapper.findAll("button").find((button) => button.text().trim() === "restart")
        expect(restartButton).toBeDefined()
        await restartButton!.trigger("click")
        await flushPromises()

        const confirmButton = wrapper.find("[data-test='dialog']").findAll("button").find((button) => button.text().trim() === "ok")
        expect(confirmButton).toBeDefined()
        await confirmButton!.trigger("click")
        await flushPromises()

        expect(queryRestart).toHaveBeenCalledOnce()
        expect(toastRequestError).toHaveBeenCalledWith(error, "errors.generic.content", "errors.generic.title")
        expect(toastSuccess).not.toHaveBeenCalled()
        expect(tableControls.toggleAllUnselected).not.toHaveBeenCalled()
        expect(tableControls.reload).not.toHaveBeenCalled()
    })

    it("reports changeStatus failures in query mode and does not reload the table", async () => {
        vi.useFakeTimers()
        const wrapper = mountExecutions()
        const executionsStore = useExecutionsStore()
        const error = new Error("Network Error")
        const queryChangeStatus = vi.spyOn(executionsStore, "queryChangeExecutionStatus").mockRejectedValue(error)

        const changeStateButton = wrapper.findAll("button").find((button) => button.text().trim() === "change state")
        expect(changeStateButton).toBeDefined()
        await changeStateButton!.trigger("click")
        await flushPromises()

        const confirmButton = wrapper.find("[data-test='dialog']").findAll("button").find((button) => button.text().trim() === "ok")
        expect(confirmButton).toBeDefined()
        await confirmButton!.trigger("click")
        await flushPromises()
        vi.advanceTimersByTime(200)

        expect(queryChangeStatus).toHaveBeenCalledOnce()
        expect(toastRequestError).toHaveBeenCalledWith(error, "errors.generic.content", "errors.generic.title")
        expect(toastSuccess).not.toHaveBeenCalled()
        expect(tableControls.toggleAllUnselected).not.toHaveBeenCalled()
        expect(tableControls.reload).not.toHaveBeenCalled()
    })

    it("reloads the table after a successful query-mode changeStatus", async () => {
        vi.useFakeTimers()
        const wrapper = mountExecutions()
        const executionsStore = useExecutionsStore()
        const queryChangeStatus = vi.spyOn(executionsStore, "queryChangeExecutionStatus").mockResolvedValue({count: 4} as never)

        const changeStateButton = wrapper.findAll("button").find((button) => button.text().trim() === "change state")
        expect(changeStateButton).toBeDefined()
        await changeStateButton!.trigger("click")
        await flushPromises()

        const confirmButton = wrapper.find("[data-test='dialog']").findAll("button").find((button) => button.text().trim() === "ok")
        expect(confirmButton).toBeDefined()
        await confirmButton!.trigger("click")
        await flushPromises()

        expect(queryChangeStatus).toHaveBeenCalledOnce()
        expect(toastRequestError).not.toHaveBeenCalled()
        expect(toastSuccess).toHaveBeenCalledOnce()
        expect(tableControls.toggleAllUnselected).toHaveBeenCalledOnce()
        expect(tableControls.reload).toHaveBeenCalledTimes(1)

        vi.advanceTimersByTime(100)
        expect(tableControls.reload).toHaveBeenCalledTimes(2)
    })
})
