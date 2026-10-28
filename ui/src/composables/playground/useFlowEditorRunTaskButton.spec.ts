import {nextTick, ref} from "vue"
import {beforeEach, describe, expect, it, vi} from "vitest"
import type {KsEditor} from "@kestra-io/design-system"

const {taskLines, playgroundStore} = vi.hoisted(() => ({
    taskLines: {} as Record<string, {start: number, end: number}>,
    playgroundStore: {enabled: true, dropdownOpened: false},
}))

vi.mock("@kestra-io/topology/flow-yaml-utils", () => ({
    getTasksLines: () => taskLines,
}))

vi.mock("../../stores/playground", () => ({
    usePlaygroundStore: () => playgroundStore,
}))

import useFlowEditorRunTaskButton from "./useFlowEditorRunTaskButton"

const setup = (source = "outside\nfirst\nmiddle\nlast\noutside\noutside") => {
    const isCurrentTabFlow = ref(true)
    const composable = useFlowEditorRunTaskButton(
        isCurrentTabFlow,
        ref<InstanceType<typeof KsEditor> | undefined>(undefined),
        ref(source),
    )

    return {composable, isCurrentTabFlow}
}

beforeEach(() => {
    for (const key of Object.keys(taskLines)) {
        delete taskLines[key]
    }
    playgroundStore.enabled = true
    playgroundStore.dropdownOpened = false
})

describe("useFlowEditorRunTaskButton", () => {
    it("resolves a line inside one task range", async () => {
        taskLines.first = {start: 2, end: 4}
        const {composable} = setup()

        composable.highlightHoveredTask(3)
        await nextTick()

        expect(composable.highlightedLines.value?.taskId).toBe("first")
    })

    it("resolves the shortest range when tasks are nested", async () => {
        taskLines.outer = {start: 1, end: 6}
        taskLines.inner = {start: 2, end: 4}
        const {composable} = setup()

        composable.highlightHoveredTask(3)
        await nextTick()

        expect(composable.highlightedLines.value?.taskId).toBe("inner")
    })

    it("does not resolve a line outside every task range", async () => {
        taskLines.first = {start: 2, end: 4}
        const {composable} = setup()

        composable.highlightHoveredTask(5)
        await nextTick()

        expect(composable.highlightedLines.value).toBeUndefined()
    })

    it("does not resolve tasks when the current tab is not a flow", async () => {
        taskLines.first = {start: 2, end: 4}
        const {composable, isCurrentTabFlow} = setup()

        composable.highlightHoveredTask(3)
        await nextTick()
        expect(composable.highlightedLines.value?.taskId).toBe("first")

        isCurrentTabFlow.value = false
        await nextTick()

        expect(composable.highlightedLines.value).toBeUndefined()
    })

    it("calculates the longest line from the hovered task source", async () => {
        taskLines.first = {start: 2, end: 3}
        const {composable} = setup("outside line longer than any task line\nshort\na much longer line\nalso outside")

        composable.highlightHoveredTask(2)
        await nextTick()

        expect(composable.highlightedLines.value?.longestLineLength).toBe("a much longer line".length)
    })

    it("calculates the first line length from the range start", async () => {
        taskLines.first = {start: 2, end: 3}
        const {composable} = setup("outside\nfirst task line\nsecond")

        composable.highlightHoveredTask(2)
        await nextTick()

        expect(composable.highlightedLines.value?.firstLineLength).toBe("first task line".length)
    })

    it.each([2, 4])("includes the range boundary at line %i", async (lineNumber) => {
        taskLines.first = {start: 2, end: 4}
        const {composable} = setup()

        composable.highlightHoveredTask(lineNumber)
        await nextTick()

        expect(composable.highlightedLines.value?.taskId).toBe("first")
    })
})
