import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import FailureUpstreamOutputs from "./FailureUpstreamOutputs.vue"
import en from "../../../translations/en.json"

const mocks = vi.hoisted(() => ({
    loadTaskRunOutputs: vi.fn(),
}))

vi.mock("../../../composables/useTaskRunOutputs", () => ({
    loadTaskRunOutputs: mocks.loadTaskRunOutputs,
}))

function taskRun(id: string, taskId: string, extra: Record<string, unknown> = {}) {
    return {id, taskId, state: {current: "SUCCESS", histories: []}, ...extra}
}

function mountOutputs(
    referencedTaskIds: string[],
    taskRunList: ReturnType<typeof taskRun>[],
    focusedTaskRun: ReturnType<typeof taskRun> = taskRun("tr-focused", "focused"),
) {
    return i18nMount(FailureUpstreamOutputs, {
        locales: {en: en.en},
        props: {referencedTaskIds, taskRunList: taskRunList as never, focusedTaskRun: focusedTaskRun as never, executionId: "exec-1"},
        global: {
            stubs: {Vars: {template: "<pre>{{ JSON.stringify(data) }}</pre>", props: ["data"]}},
        },
    })
}

function deferred<T>() {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((res) => { resolve = res })
    return {promise, resolve}
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

    it("should keep the current references' outputs when an earlier load answers last", async () => {
        const first = deferred<Record<string, unknown>>()
        const second = deferred<Record<string, unknown>>()
        mocks.loadTaskRunOutputs.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

        const wrapper = mountOutputs(["extract"], [taskRun("tr-extract", "extract"), taskRun("tr-load", "load")])
        await wrapper.setProps({referencedTaskIds: ["load"]})
        second.resolve({rows: "from load"})
        await flushPromises()
        first.resolve({rows: "from extract"})
        await flushPromises()

        expect(wrapper.text()).toContain("load.rows")
        expect(wrapper.text()).not.toContain("extract.rows")
    })

    it("should read a referenced task's outputs from the focused run's iteration, not the first one", async () => {
        mocks.loadTaskRunOutputs.mockImplementation((_executionId: string, taskRunId: string) =>
            Promise.resolve({rows: taskRunId}),
        )

        const wrapper = mountOutputs(
            ["t1"],
            [
                taskRun("t1-a", "t1", {value: "a", parentTaskRunId: "each"}),
                taskRun("t1-b", "t1", {value: "b", parentTaskRunId: "each"}),
            ],
            taskRun("t2-b", "t2", {value: "b", parentTaskRunId: "each"}),
        )
        await flushPromises()

        expect(mocks.loadTaskRunOutputs).toHaveBeenCalledWith("exec-1", "t1-b")
        expect(wrapper.text()).toContain("t1-b")
    })

    it("should prefer a referenced run sharing the focused run's parent when no value matches", async () => {
        mocks.loadTaskRunOutputs.mockResolvedValue({})

        mountOutputs(
            ["t1"],
            [
                taskRun("t1-other", "t1", {parentTaskRunId: "branch-1"}),
                taskRun("t1-same", "t1", {parentTaskRunId: "branch-2"}),
            ],
            taskRun("t2", "t2", {parentTaskRunId: "branch-2"}),
        )
        await flushPromises()

        expect(mocks.loadTaskRunOutputs).toHaveBeenCalledWith("exec-1", "t1-same")
    })
})
