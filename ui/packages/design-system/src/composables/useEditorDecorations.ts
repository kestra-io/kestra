import {watch, type Ref} from "vue"
import * as monaco from "monaco-editor/esm/vs/editor/editor.api"

const PEBBLE_BLOCK_PATTERN = "\\{\\{(.+?)}}"

type CodeEditor = monaco.editor.IStandaloneCodeEditor

export interface EditorDecorationsContext {
    pebbleEnabled: Ref<boolean>
    highlightLine: Ref<number | undefined>
    initialHighlight: Ref<string | undefined>
    codeEditor: () => CodeEditor | undefined
    modifiedEditor: () => CodeEditor | undefined
}

export function useEditorDecorations(ctx: EditorDecorationsContext) {
    let collection: monaco.editor.IEditorDecorationsCollection | undefined
    const lists: {
        pebble?: monaco.editor.IModelDeltaDecoration[]
        lines?: monaco.editor.IModelDeltaDecoration[]
    } = {}

    function attach(editor: monaco.editor.IStandaloneCodeEditor | monaco.editor.IStandaloneDiffEditor) {
        collection = editor.createDecorationsCollection()
    }

    function apply() {
        collection?.clear()
        if (lists.lines) collection?.append(lists.lines)
        if (lists.pebble) collection?.append(lists.pebble)
    }

    function highlightPebble() {
        if (!ctx.pebbleEnabled.value) {
            lists.pebble = []
            apply()
            return
        }
        const editor = ctx.codeEditor()
        if (!editor) return
        const model = editor.getModel?.()
        const text = model?.getValue?.()
        if (!text || !model) return

        const regex = new RegExp(PEBBLE_BLOCK_PATTERN, "g")
        const found: monaco.editor.IModelDeltaDecoration[] = []
        let match
        while ((match = regex.exec(text)) !== null) {
            const startPos = model.getPositionAt(match.index)
            const endPos = model.getPositionAt(match.index + match[0].length)
            found.push({
                range: {
                    startLineNumber: startPos.lineNumber,
                    startColumn: startPos.column,
                    endLineNumber: endPos.lineNumber,
                    endColumn: endPos.column,
                },
                options: {inlineClassName: "highlight-pebble"},
            })
        }
        lists.pebble = found
        apply()
    }

    function highlightLinesRange(range: {start: number, end: number}) {
        lists.lines = [{
            range: new monaco.Range(range.start, 1, range.end, 1),
            options: {isWholeLine: true, className: "highlight-lines"},
        }]
        apply()
    }

    function clearLinesRangeHighlights() {
        lists.lines = []
        apply()
    }

    function selectLine(line: number) {
        const editor = ctx.modifiedEditor()
        if (!editor) return
        editor.focus()
        const end = editor.getModel()?.getLineMaxColumn(line) ?? 0
        editor.setSelection(new monaco.Range(line, 0, line, end))
    }

    function highlightInitial() {
        const needle = ctx.initialHighlight.value
        if (!needle) return
        const editor = ctx.modifiedEditor()
        if (!editor) return

        editor.focus()
        const lines = editor.getModel()!.getLinesContent()
        let lineNumber = 0
        for (let i = 0; i < lines.length; i++) {
            if (lines[i].includes(needle)) {
                lineNumber = i + 1
                break
            }
        }
        const endLineCharacter = editor.getModel()!.getLineMaxColumn(lineNumber) ?? 0
        editor.setSelection(new monaco.Range(lineNumber, 0, lineNumber, endLineCharacter))
        editor.revealLineInCenter(lineNumber)
    }

    watch(ctx.highlightLine, (line) => {
        if (line) selectLine(line)
    })

    return {attach, highlightPebble, highlightLinesRange, clearLinesRangeHighlights, highlightInitial}
}
