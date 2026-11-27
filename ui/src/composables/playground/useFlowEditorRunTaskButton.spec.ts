import {nextTick, ref} from "vue"
import {beforeEach, describe, expect, it, vi} from "vitest"
import type {KsEditor} from "@kestra-io/design-system"

const {taskLines, playgroundStore, flowStore} = vi.hoisted(() => ({
    taskLines: {} as Record<string, {start: number, end: number}>,
    playgroundStore: {available: true, runUntilTask: vi.fn()},
    flowStore: {isReadOnly: false},
}))

vi.mock("@kestra-io/topology/flow-yaml-utils", () => ({
    getTasksLines: () => taskLines,
}))
vi.mock("vue-i18n", () => ({useI18n: () => ({t: (key: string, values: {id: string}) => `${key}:${values.id}`})}))
vi.mock("../../stores/playground", () => ({usePlaygroundStore: () => playgroundStore}))
vi.mock("../../stores/flow", () => ({useFlowStore: () => flowStore}))

import useFlowEditorRunTaskButton from "./useFlowEditorRunTaskButton"

const setGutterActions = vi.fn()

const setup = () => {
    const isCurrentTabFlow = ref(true)
    const editor = ref({setGutterActions} as unknown as InstanceType<typeof KsEditor>)
    const composable = useFlowEditorRunTaskButton(isCurrentTabFlow, editor, ref("source"))
    return {composable, isCurrentTabFlow}
}

beforeEach(() => {
    for (const key of Object.keys(taskLines)) delete taskLines[key]
    playgroundStore.available = true
    playgroundStore.runUntilTask.mockClear()
    flowStore.isReadOnly = false
    setGutterActions.mockClear()
})

describe("useFlowEditorRunTaskButton", () => {
    it("puts a run action on the first line of every task, nested ones included", () => {
        taskLines.parent = {start: 2, end: 9}
        taskLines.child = {start: 5, end: 7}

        setup()

        expect(setGutterActions).toHaveBeenLastCalledWith([
            {id: "parent", line: 2, title: "playground.run_task_gutter:parent"},
            {id: "child", line: 5, title: "playground.run_task_gutter:child"},
        ])
    })

    it.each([
        ["the tab is a namespace file", () => ({isCurrentTabFlow: false})],
        ["the user turned the Playground off", () => { playgroundStore.available = false; return {} }],
        ["the flow is read-only", () => { flowStore.isReadOnly = true; return {} }],
    ])("offers no run action when %s", async (_case, arrange) => {
        taskLines.first = {start: 2, end: 4}
        const {isCurrentTabFlow: flowTab} = arrange() as {isCurrentTabFlow?: boolean}

        const {isCurrentTabFlow} = setup()
        if (flowTab === false) isCurrentTabFlow.value = false
        await nextTick()

        expect(setGutterActions).toHaveBeenLastCalledWith([])
    })

    it.each([[false], [true]])("runs the clicked task, with its downstream tasks only on Shift+click (shift=%s)", (shiftKey) => {
        const {composable} = setup()

        composable.runGutterAction({id: "first", shiftKey})

        expect(playgroundStore.runUntilTask).toHaveBeenCalledExactlyOnceWith("first", shiftKey)
    })
})
