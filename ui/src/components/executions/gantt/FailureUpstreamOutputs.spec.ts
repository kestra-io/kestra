import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import FailureUpstreamOutputs from "./FailureUpstreamOutputs.vue"
import en from "../../../translations/en.json"

const mocks = vi.hoisted(() => ({
    loadTaskRunOutputs: vi.fn(),
}))

vi.mock("../../../composables/useTaskRunOutputs", () => ({
    loadTaskRunOutputs: mocks.loadTaskRunOutputs,
}))

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: {en: en.en}})

function taskRun(id: string, taskId: string) {
    return {id, taskId, state: {current: "SUCCESS", histories: []}}
}

function mountOutputs(referencedTaskIds: string[], taskRunList: ReturnType<typeof taskRun>[]) {
    return mount(FailureUpstreamOutputs, {
        props: {referencedTaskIds, taskRunList: taskRunList as never, executionId: "exec-1"},
        global: {
            plugins: [i18n],
            // Vars.vue's KsTable/KsTableColumn scoped-slot rendering needs the real design-system
            // plugin (covered by the Storybook story instead) — it throws in jsdom without it.
            stubs: {Vars: {template: "<pre>{{ JSON.stringify(data) }}</pre>", props: ["data"]}},
        },
    })
}

describe("FailureUpstreamOutputs", () => {
    afterEach(() => {
        vi.clearAllMocks()
    })

    it("should show an empty state when the task references no other task's outputs", async () => {
        const wrapper = mountOutputs([], [taskRun("tr-1", "extract")])
        await flushPromises()

        expect(mocks.loadTaskRunOutputs).not.toHaveBeenCalled()
        expect(wrapper.text()).toContain("does not reference")
    })

    it("should fetch and merge outputs, prefixed by task id, for each referenced task", async () => {
        mocks.loadTaskRunOutputs.mockImplementation((_executionId: string, taskRunId: string) =>
            Promise.resolve(taskRunId === "tr-extract" ? {rows: 48203} : {}),
        )

        const wrapper = mountOutputs(
            ["extract"],
            [taskRun("tr-extract", "extract"), taskRun("tr-load", "load")],
        )
        await flushPromises()

        expect(mocks.loadTaskRunOutputs).toHaveBeenCalledWith("exec-1", "tr-extract")
        expect(wrapper.text()).toContain("extract.rows")
        expect(wrapper.text()).toContain("48203")
    })

    it("should skip a referenced task id that never actually ran", async () => {
        const wrapper = mountOutputs(["never_ran"], [taskRun("tr-1", "extract")])
        await flushPromises()

        expect(mocks.loadTaskRunOutputs).not.toHaveBeenCalled()
        expect(wrapper.text()).toContain("does not reference")
    })

    it("should show an error state when loading outputs fails", async () => {
        mocks.loadTaskRunOutputs.mockRejectedValue(new Error("network error"))

        const wrapper = mountOutputs(["extract"], [taskRun("tr-extract", "extract")])
        await flushPromises()

        expect(wrapper.text()).toContain("Could not load")
    })
})
