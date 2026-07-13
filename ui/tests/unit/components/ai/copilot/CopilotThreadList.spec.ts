import {describe, it, expect, vi, beforeEach} from "vitest"
import {mount} from "@vue/test-utils"
import {ref} from "vue"
import {mountGlobal} from "./_helpers"

// Drive the list from the test via a mocked useAiThreads (no real client).
const state = {
    threads: ref<any[]>([]),
    loading: ref(false),
    error: ref(false),
    list: vi.fn(),
    rename: vi.fn(),
    remove: vi.fn(),
}
vi.mock("../../../../../src/components/ai/copilot/useAiThreads", () => ({useAiThreads: () => state}))

import CopilotThreadList from "../../../../../src/components/ai/copilot/CopilotThreadList.vue"

const mountList = (props = {}) => mount(CopilotThreadList, {props, global: mountGlobal})

describe("CopilotThreadList", () => {
    beforeEach(() => {
        state.threads.value = []
        state.loading.value = false
        state.list.mockReset()
        state.rename.mockReset()
        state.remove.mockReset()
    })

    it("loads the list on mount", () => {
        mountList()
        expect(state.list).toHaveBeenCalled()
    })

    it("shows the empty state when there are no threads", () => {
        expect(mountList().text()).toContain("No conversations yet")
    })

    it("renders threads and emits select on click", async () => {
        state.threads.value = [{uid: "t1", title: "First chat"}]
        const w = mountList()
        expect(w.text()).toContain("First chat")
        await w.find("[data-test=\"copilot-thread-select\"]").trigger("click")
        expect(w.emitted("select")?.[0]).toEqual(["t1"])
    })

    it("renames a thread", async () => {
        state.threads.value = [{uid: "t1", title: "Old"}]
        const w = mountList()
        await w.find("[data-test=\"copilot-thread-rename\"]").trigger("click")
        await w.find("textarea").setValue("New name")
        await w.find("[data-test=\"copilot-thread-rename-save\"]").trigger("click")
        expect(state.rename).toHaveBeenCalledWith("t1", "New name")
    })

    it("deletes a thread after an inline confirm", async () => {
        state.threads.value = [{uid: "t1", title: "X"}]
        const w = mountList()
        await w.find("[data-test=\"copilot-thread-delete\"]").trigger("click")
        await w.find("[data-test=\"copilot-thread-delete-confirm\"]").trigger("click")
        expect(state.remove).toHaveBeenCalledWith("t1")
    })
})
