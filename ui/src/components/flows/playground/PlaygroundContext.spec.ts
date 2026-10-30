import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {reactive} from "vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const executionsStore = reactive({execution: undefined as Record<string, unknown> | undefined})
const playgroundApi = vi.hoisted(() => ({taskRunContext: vi.fn()}))

vi.mock("@kestra-io/kestra-sdk/playground", () => playgroundApi)
vi.mock("../../../stores/executions", () => ({useExecutionsStore: () => executionsStore}))

import PlaygroundContext from "./PlaygroundContext.vue"

const stoppedAtB = {
    id: "e1",
    namespace: "ns",
    flowId: "f",
    state: {current: "BREAKPOINT"},
    taskRunList: [
        {id: "tr-a", taskId: "a", state: {current: "SUCCESS"}},
        {id: "tr-b", taskId: "b", state: {current: "BREAKPOINT"}},
    ],
}

const messages = {playground: {context: {empty: "Nothing ran yet", error: "Context failed", task_run: "Task run"}}}

describe("PlaygroundContext", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.clearAllMocks()
        executionsStore.execution = undefined
    })

    it("shows what the task the run stopped before sees", async () => {
        playgroundApi.taskRunContext.mockResolvedValue({variables: {outputs: {a: {value: "a"}}}})
        executionsStore.execution = stoppedAtB

        const wrapper = i18nMount(PlaygroundContext, {messages})
        await flushPromises()

        expect(playgroundApi.taskRunContext).toHaveBeenCalledWith({namespace: "ns", id: "f", executionId: "e1", taskRunId: "tr-b"})
        expect(wrapper.find("[data-test=playground-context-tree]").text()).toContain("outputs")
    })

    it("says so when the context cannot be loaded", async () => {
        playgroundApi.taskRunContext.mockRejectedValue({response: {status: 404}})
        executionsStore.execution = stoppedAtB

        const wrapper = i18nMount(PlaygroundContext, {messages})
        await flushPromises()

        expect(wrapper.text()).toContain("Context failed")
    })

    it("invites a first run when there is no task run yet", () => {
        const wrapper = i18nMount(PlaygroundContext, {messages})

        expect(wrapper.text()).toContain("Nothing ran yet")
        expect(playgroundApi.taskRunContext).not.toHaveBeenCalled()
    })
})
