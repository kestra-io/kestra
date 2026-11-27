import {describe, expect, it, vi} from "vitest"
import * as monaco from "monaco-editor/editor/editor.api"
import {GUTTER_ACTION_CLASS, useEditorGutterActions} from "./useEditorGutterActions"

type MouseDown = (event: {target: {type: number, position?: {lineNumber: number}}, event: {shiftKey: boolean, preventDefault: () => void}}) => void

function fakeEditor() {
    const collection = {set: vi.fn()}
    let mouseDown: MouseDown = () => {}
    const editor = {
        updateOptions: vi.fn(),
        createDecorationsCollection: () => collection,
        onMouseDown: (handler: MouseDown) => { mouseDown = handler },
    }
    const press = (type: number, lineNumber: number, shiftKey = false) =>
        mouseDown({target: {type, position: {lineNumber}}, event: {shiftKey, preventDefault: () => {}}})
    return {editor: editor as unknown as monaco.editor.IStandaloneCodeEditor, updateOptions: editor.updateOptions, collection, press}
}

const GLYPH = monaco.editor.MouseTargetType.GUTTER_GLYPH_MARGIN

describe("useEditorGutterActions", () => {
    it("draws one glyph per action and only keeps the glyph margin while there are actions", () => {
        const {editor, updateOptions, collection} = fakeEditor()
        const {attach, setGutterActions} = useEditorGutterActions(vi.fn())
        attach(editor)

        setGutterActions([{id: "a", line: 3, title: "Run a"}, {id: "b", line: 7}])

        expect(updateOptions).toHaveBeenLastCalledWith({glyphMargin: true})
        const decorations = collection.set.mock.lastCall![0]
        expect(decorations.map((d: monaco.editor.IModelDeltaDecoration) => [d.range.startLineNumber, d.options.glyphMarginClassName]))
            .toEqual([[3, GUTTER_ACTION_CLASS], [7, GUTTER_ACTION_CLASS]])

        setGutterActions([])
        expect(updateOptions).toHaveBeenLastCalledWith({glyphMargin: false})
    })

    it("paints actions set before the editor mounted", () => {
        const {editor, collection} = fakeEditor()
        const {attach, setGutterActions} = useEditorGutterActions(vi.fn())

        setGutterActions([{id: "a", line: 2}])
        attach(editor)

        expect(collection.set.mock.lastCall![0]).toHaveLength(1)
    })

    it("reports a click on an action's glyph, with its shift state, and nothing else", () => {
        const {editor, press} = fakeEditor()
        const onAction = vi.fn()
        const {attach, setGutterActions} = useEditorGutterActions(onAction)
        attach(editor)
        setGutterActions([{id: "a", line: 3}])

        press(monaco.editor.MouseTargetType.CONTENT_TEXT, 3)
        press(GLYPH, 4)
        expect(onAction).not.toHaveBeenCalled()

        press(GLYPH, 3, true)
        expect(onAction).toHaveBeenCalledExactlyOnceWith({id: "a", shiftKey: true})
    })
})
