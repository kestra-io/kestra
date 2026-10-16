import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {ref} from "vue"
import type * as Monaco from "monaco-editor/editor/editor.api"
import {useFlowYamlEditor} from "./useFlowYamlEditor"

const {setModelMarkers} = vi.hoisted(() => ({setModelMarkers: vi.fn()}))

vi.mock("monaco-editor/editor/editor.api", () => ({
    KeyCode: {Tab: 2, Enter: 3},
    MarkerSeverity: {Error: 8},
    editor: {setModelMarkers},
}))

const TAB = 2
const ENTER = 3

const DUPLICATE_IDS = `id: flow
namespace: company.team
tasks:
  - id: same
    type: io.kestra.plugin.core.log.Log
  - id: same
    type: io.kestra.plugin.core.log.Log
`

function fakeEditor(line: string) {
    let onKeyDown: ((e: {keyCode: number, preventDefault: () => void, stopPropagation: () => void}) => void) | undefined
    const model = {getLineContent: () => line}
    const editor = {
        onKeyDown: (listener: typeof onKeyDown) => {
            onKeyDown = listener
            return {dispose: () => {}}
        },
        getModel: () => model,
        getPosition: () => ({lineNumber: 1, column: line.length + 1}),
        getDomNode: () => document.createElement("div"),
        trigger: vi.fn(),
    }
    const press = (keyCode: number) => onKeyDown?.({keyCode, preventDefault: vi.fn(), stopPropagation: vi.fn()})
    return {editor: editor as unknown as Monaco.editor.IStandaloneCodeEditor, model, trigger: editor.trigger, press}
}

describe("useFlowYamlEditor", () => {
    beforeEach(() => {
        vi.useFakeTimers()
        setModelMarkers.mockClear()
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it.each([["Tab", TAB], ["Enter", ENTER]])("opens the next line and asks for an inline suggestion when %s accepts a type", (_, keyCode) => {
        const {editor, trigger, press} = fakeEditor("  - type: io.kestra.plugin.core.log.Log")
        useFlowYamlEditor({isFlowYaml: true, duplicateTaskIdMarkers: undefined, value: "", codeEditor: editor}).attach(editor, () => true)

        press(keyCode)
        vi.runAllTimers()

        expect(trigger).toHaveBeenCalledWith("typeAcceptedInsertLine", "editor.action.insertLineAfter", {})
        expect(trigger).toHaveBeenCalledWith("typeAcceptedInlineSuggest", "editor.action.inlineSuggest.trigger", {})
    })

    it("does nothing on Tab or Enter when the editor is not flow YAML", () => {
        const {editor, trigger, press} = fakeEditor("  - type: io.kestra.plugin.core.log.Log")
        const flowYaml = useFlowYamlEditor({isFlowYaml: false, duplicateTaskIdMarkers: undefined, value: "", codeEditor: editor})
        flowYaml.attach(editor, () => true)

        press(TAB)
        press(ENTER)
        vi.runAllTimers()

        expect(trigger).not.toHaveBeenCalled()
        expect(flowYaml.constructionOptions.value).toEqual({})
    })

    it.each([
        {isFlowYaml: true, duplicateTaskIdMarkers: undefined, expected: 1},
        {isFlowYaml: false, duplicateTaskIdMarkers: undefined, expected: 0},
        {isFlowYaml: true, duplicateTaskIdMarkers: false, expected: 0},
        {isFlowYaml: false, duplicateTaskIdMarkers: true, expected: 1},
    ])("marks duplicate task ids $expected time(s) for flow YAML $isFlowYaml and duplicateTaskIdMarkers $duplicateTaskIdMarkers", ({isFlowYaml, duplicateTaskIdMarkers, expected}) => {
        const {editor, model} = fakeEditor("")
        useFlowYamlEditor({isFlowYaml, duplicateTaskIdMarkers, value: DUPLICATE_IDS, codeEditor: editor})

        expect(setModelMarkers).toHaveBeenCalledTimes(expected)
        if (expected) {
            expect(setModelMarkers).toHaveBeenCalledWith(model, "duplicate-task-ids", [expect.objectContaining({message: expect.stringContaining("same")})])
        }
    })

    it("refreshes the duplicate task id markers when the value changes", async () => {
        const {editor, model} = fakeEditor("")
        const value = ref(DUPLICATE_IDS)
        useFlowYamlEditor({isFlowYaml: true, duplicateTaskIdMarkers: undefined, value, codeEditor: editor})

        value.value = DUPLICATE_IDS.replace("id: same\n    type: io.kestra.plugin.core.log.Log\n", "id: other\n    type: io.kestra.plugin.core.log.Log\n")
        await vi.runAllTimersAsync()

        expect(setModelMarkers).toHaveBeenLastCalledWith(model, "duplicate-task-ids", [])
    })
})
