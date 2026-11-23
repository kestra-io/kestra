import {beforeEach, describe, expect, it, vi} from "vitest"
import {setActivePinia} from "pinia"
import {flushPromises} from "@vue/test-utils"
import FailedTaskCard from "./FailedTaskCard.vue"
import {useExecutionsStore, type Execution} from "../../../../stores/executions"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import {routedPinia} from "../../../../../tests/unit/memoryRouter"

const messages = {execution_mobile: {failed_task: "Failed task", view_task_logs: "View task logs"}}

const execution = (state: string) => ({
    id: "e1",
    namespace: "ns",
    flowId: "flow",
    state: {current: state},
    taskRunList: [
        {id: "tr-parent", taskId: "per_region", value: "#3", state: {current: state}},
        {id: "tr-1", taskId: "reconcile", parentTaskRunId: "tr-parent", state: {current: state}},
    ],
} as unknown as Execution)

const mountCard = (state = "FAILED") => i18nMount(FailedTaskCard, {
    messages,
    props: {execution: execution(state)},
    global: {stubs: {RouterLink: {template: "<a><slot /></a>"}}},
})

describe("FailedTaskCard", () => {
    beforeEach(() => {
        setActivePinia(routedPinia())
        vi.spyOn(useExecutionsStore(), "loadLogs").mockResolvedValue([{taskRunId: "tr-1", message: "Ledger is off"}] as never)
    })

    it("shows the task path, the first error and the way to its logs", async () => {
        // Given / When
        const wrapper = mountCard()
        await flushPromises()

        // Then
        expect(wrapper.text()).toContain("per_region #3 › reconcile")
        expect(wrapper.text()).toContain("Ledger is off")
        expect(wrapper.find("[data-test=\"view-task-logs\"]").text()).toBe("View task logs")
    })

    it("renders nothing when the execution did not fail", () => {
        // Given / When
        const wrapper = mountCard("SUCCESS")

        // Then
        expect(wrapper.find("[data-test=\"failed-task-card\"]").exists()).toBe(false)
    })
})
