import {beforeEach, describe, expect, it, vi} from "vitest"

const {playgroundStore} = vi.hoisted(() => ({
    playgroundStore: {runUntilTask: vi.fn()},
}))

vi.mock("../../stores/playground", () => ({
    usePlaygroundStore: () => playgroundStore,
}))

import {usePlaygroundRun} from "./usePlaygroundRun"

beforeEach(() => {
    playgroundStore.runUntilTask.mockClear()
})

describe("usePlaygroundRun", () => {
    it("runs until the given task", () => {
        usePlaygroundRun().runTask("my-task")

        expect(playgroundStore.runUntilTask).toHaveBeenCalledWith("my-task", false)
    })

    it("forwards downstream when passed", () => {
        usePlaygroundRun().runTask("my-task", true)

        expect(playgroundStore.runUntilTask).toHaveBeenCalledWith("my-task", true)
    })

    it("does not call the store without a task id", () => {
        usePlaygroundRun().runTask()

        expect(playgroundStore.runUntilTask).not.toHaveBeenCalled()
    })

    it("treats an empty task id as missing", () => {
        usePlaygroundRun().runTask("")

        expect(playgroundStore.runUntilTask).not.toHaveBeenCalled()
    })

    it("exposes the playground store it used", () => {
        expect(usePlaygroundRun().playgroundStore).toBe(playgroundStore)
    })
})
