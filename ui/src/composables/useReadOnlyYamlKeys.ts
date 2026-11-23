import {onBeforeUnmount, watch, type Ref} from "vue"
import type * as monaco from "monaco-editor/editor/editor.api"

const TOP_LEVEL_KEY = /^([A-Za-z0-9_-]+)[ \t]*:(.*)$/

export const READ_ONLY_LINE_CLASS = "ks-readonly-yaml-line"

export interface ReadOnlyKeyLine {
    key: string
    lineNumber: number
}

interface Scalar {
    value: string
    suffix: string
}

function splitScalar(raw: string): Scalar {
    const trimmed = raw.trim()
    const quote = trimmed[0]

    if (quote === "\"" || quote === "'") {
        const closing = trimmed.indexOf(quote, 1)
        return closing === -1
            ? {value: trimmed.slice(1), suffix: ""}
            : {value: trimmed.slice(1, closing), suffix: trimmed.slice(closing + 1)}
    }

    const comment = trimmed.search(/\s#/)
    return comment === -1
        ? {value: trimmed, suffix: ""}
        : {value: trimmed.slice(0, comment).trim(), suffix: trimmed.slice(comment)}
}

export function commentOf(raw: string): string {
    return splitScalar(raw).suffix
}

export function readTopLevelValue(source: string, key: string): string | undefined {
    for (const rawLine of source.split("\n")) {
        const match = TOP_LEVEL_KEY.exec(rawLine.replace(/\r$/, ""))
        if (match?.[1] !== key) continue
        return splitScalar(match[2]).value
    }
    return undefined
}

export function findReadOnlyLines(source: string, keys: readonly string[]): ReadOnlyKeyLine[] {
    const wanted = new Set(keys)
    const seen = new Set<string>()
    const lines: ReadOnlyKeyLine[] = []

    source.split("\n").forEach((rawLine, index) => {
        const key = TOP_LEVEL_KEY.exec(rawLine.replace(/\r$/, ""))?.[1]
        if (!key || !wanted.has(key) || seen.has(key)) return
        seen.add(key)
        lines.push({key, lineNumber: index + 1})
    })

    return lines
}

export function violatedKeys(
    source: string,
    expected: Record<string, string | undefined>,
): string[] {
    return Object.entries(expected)
        .filter(([key, value]) => {
            if (value === undefined) return false
            return readTopLevelValue(source, key) !== value
        })
        .map(([key]) => key)
}

export interface ReadOnlyYamlKeysOptions {
    /** Must be a shallowRef: a deep reactive proxy around the editor breaks it. */
    editor: Ref<monaco.editor.IStandaloneCodeEditor | undefined>
    expected: Ref<Record<string, string | undefined>>
    enabled: Ref<boolean>
    hoverMessage?: Ref<string | undefined>
}

export function useReadOnlyYamlKeys(options: ReadOnlyYamlKeysOptions) {
    let contentListener: monaco.IDisposable | undefined
    let selectionListener: monaco.IDisposable | undefined
    let modelListener: monaco.IDisposable | undefined
    let decorations: monaco.editor.IEditorDecorationsCollection | undefined
    let previous = ""
    let selectionsBeforeEdit: monaco.Selection[] = []
    let correcting = false

    function keys(): string[] {
        return Object.keys(options.expected.value)
    }

    function paint(editor: monaco.editor.IStandaloneCodeEditor, source: string) {
        decorations ??= editor.createDecorationsCollection()

        if (!options.enabled.value) {
            decorations.clear()
            return
        }

        const hover = options.hoverMessage?.value
        decorations.set(findReadOnlyLines(source, keys()).map((line) => ({
            range: {
                startLineNumber: line.lineNumber,
                startColumn: 1,
                endLineNumber: line.lineNumber,
                endColumn: 1,
            },
            options: {
                isWholeLine: true,
                className: READ_ONLY_LINE_CLASS,
                ...(hover ? {hoverMessage: {value: hover}} : {}),
            },
        })))
    }

    function inverseOf(
        model: monaco.editor.ITextModel,
        changes: readonly monaco.editor.IModelContentChange[],
    ): monaco.editor.IIdentifiedSingleEditOperation[] {
        let shift = 0
        return [...changes]
            .sort((a, b) => a.rangeOffset - b.rangeOffset)
            .map((change) => {
                const start = model.getPositionAt(change.rangeOffset + shift)
                const end = model.getPositionAt(change.rangeOffset + shift + change.text.length)
                shift += change.text.length - change.rangeLength
                return {
                    range: {
                        startLineNumber: start.lineNumber,
                        startColumn: start.column,
                        endLineNumber: end.lineNumber,
                        endColumn: end.column,
                    },
                    text: previous.slice(change.rangeOffset, change.rangeOffset + change.rangeLength),
                }
            })
    }

    function introducesViolation(current: string): boolean {
        const alreadyViolated = new Set(violatedKeys(previous, options.expected.value))
        return violatedKeys(current, options.expected.value).some((key) => !alreadyViolated.has(key))
    }

    function onContentChange(
        editor: monaco.editor.IStandaloneCodeEditor,
        event: monaco.editor.IModelContentChangedEvent,
    ) {
        if (correcting) return

        const model = editor.getModel()
        if (!model) return

        const current = model.getValue()

        if (!options.enabled.value || event.isFlush || !introducesViolation(current)) {
            previous = current
            paint(editor, current)
            return
        }

        correcting = true
        try {
            model.pushEditOperations(selectionsBeforeEdit, inverseOf(model, event.changes), () => null)
            if (selectionsBeforeEdit.length) editor.setSelections(selectionsBeforeEdit)
        } finally {
            correcting = false
        }

        previous = model.getValue()
        paint(editor, previous)
    }

    function detach() {
        contentListener?.dispose()
        selectionListener?.dispose()
        modelListener?.dispose()
        contentListener = selectionListener = modelListener = undefined
        decorations?.clear()
        decorations = undefined
    }

    function attach(editor: monaco.editor.IStandaloneCodeEditor) {
        detach()

        function rebase() {
            previous = editor.getModel()?.getValue() ?? ""
            selectionsBeforeEdit = editor.getSelections() ?? []
            paint(editor, previous)
        }

        rebase()

        contentListener = editor.onDidChangeModelContent((event) => onContentChange(editor, event))
        selectionListener = editor.onDidChangeCursorSelection(() => {
            if (!correcting) selectionsBeforeEdit = editor.getSelections() ?? []
        })
        modelListener = editor.onDidChangeModel(rebase)
    }

    watch(
        [options.editor, options.enabled, () => JSON.stringify(options.expected.value)],
        ([editor]) => {
            if (!editor) {
                detach()
                return
            }
            attach(editor)
        },
        {immediate: true},
    )

    onBeforeUnmount(detach)

    return {detach}
}
