import {onBeforeUnmount, watch, type Ref} from "vue"
import type * as monaco from "monaco-editor/editor/editor.api"

// Anchored at column 0: block-scalar content is always indented, so an unindented
// `id:` can never be part of a multi-line string value.
const TOP_LEVEL_KEY = /^([A-Za-z0-9_-]+)[ \t]*:(.*)$/

/** The class placed on each locked line; styled by KsEditor in the design system. */
export const READ_ONLY_LINE_CLASS = "ks-readonly-yaml-line"

export interface ReadOnlyKeyLine {
    key: string
    /** 1-based, matching Monaco's line numbering. */
    lineNumber: number
}

interface Scalar {
    value: string
    /** The quote the value was written with, or "" if it was bare. */
    quote: string
    /** Whatever trailed the value, in practice an inline comment. */
    suffix: string
}

// Quotes and comments are split off rather than discarded, so a correction can put
// back the ones the user wrote.
function splitScalar(raw: string): Scalar {
    const trimmed = raw.trim()
    const quote = trimmed[0]

    if (quote === "\"" || quote === "'") {
        const closing = trimmed.indexOf(quote, 1)
        // A quote still being typed has no closing partner yet.
        return closing === -1
            ? {value: trimmed.slice(1), quote, suffix: ""}
            : {value: trimmed.slice(1, closing), quote, suffix: trimmed.slice(closing + 1)}
    }

    // YAML only opens an inline comment on a `#` preceded by whitespace, so the `#`
    // in `id: a#b` belongs to the value.
    const comment = trimmed.search(/\s#/)
    return comment === -1
        ? {value: trimmed, quote: "", suffix: ""}
        : {value: trimmed.slice(0, comment).trim(), quote: "", suffix: trimmed.slice(comment)}
}

/** Whatever trails the scalar on a `key:` line — an inline comment, or "". */
export function commentOf(raw: string): string {
    return splitScalar(raw).suffix
}

// Only the first occurrence counts: a duplicate top-level key is invalid YAML.
export function readTopLevelValue(source: string, key: string): string | undefined {
    for (const rawLine of source.split("\n")) {
        const match = TOP_LEVEL_KEY.exec(rawLine.replace(/\r$/, ""))
        if (match?.[1] !== key) continue
        return splitScalar(match[2]).value
    }
    return undefined
}

/** Locate the lines holding the given keys. Keys not present are not returned. */
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

// Compared against the expected values rather than the previous text, so a legitimate
// whole-document replacement — another flow, a restored revision — is not read as a
// violation; an expectation of `undefined` is not known yet and never fails.
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
    /** Expected value per locked key. Undefined values disable the guard for that key. */
    expected: Ref<Record<string, string | undefined>>
    /** Guard only applies while true — creation flows stay fully editable. */
    enabled: Ref<boolean>
    /** Tooltip shown when hovering a locked line. */
    hoverMessage?: Ref<string | undefined>
    /** Called with the offending keys when a change was reverted wholesale rather than corrected in place. */
    onReverted?: (keys: string[]) => void
}

/**
 * Keep the given top-level YAML keys read-only inside a Monaco editor.
 *
 * Monaco has no read-only-range API, so the guard refuses an offending change on the
 * tick it arrives rather than letting it land and undoing it a moment later, and it
 * rewrites only the locked lines so the rest of an edit spanning them survives.
 *
 * Known dead end: when the buffer already violates as the guard attaches there is no
 * good content to fall back to, so an edit that removes a locked key outright then
 * stands, unreported, until the next clean edit gives the guard something to keep.
 */
export function useReadOnlyYamlKeys(options: ReadOnlyYamlKeysOptions) {
    let changeListener: monaco.IDisposable | undefined
    let decorations: monaco.editor.IEditorDecorationsCollection | undefined
    // Last content known to satisfy every expectation; what the fallback restores.
    let lastValid: string | undefined
    // Guards against reacting to our own corrective edit.
    let correcting = false

    function keys(): string[] {
        return Object.keys(options.expected.value)
    }

    // pushEditOperations joins the undo element Monaco still has open for the user's
    // keystroke, so one undo reverts both halves; executeEdits would open a second
    // element, and undoing that would restore the violating text.
    function applyCorrection(
        editor: monaco.editor.IStandaloneCodeEditor,
        model: monaco.editor.ITextModel,
        edits: monaco.editor.IIdentifiedSingleEditOperation[],
    ) {
        model.pushEditOperations(editor.getSelections() ?? [], edits, () => null)
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

    // False when a locked key is gone altogether, which cannot be corrected line by line.
    function restoreLines(
        editor: monaco.editor.IStandaloneCodeEditor,
        model: monaco.editor.ITextModel,
        violations: string[],
    ): boolean {
        const source = model.getValue()
        const sourceLines = source.split("\n")
        const edits: monaco.editor.IIdentifiedSingleEditOperation[] = []

        for (const key of violations) {
            const line = findReadOnlyLines(source, [key])[0]
            if (!line) return false

            const rawLine = sourceLines[line.lineNumber - 1] ?? ""
            const afterKey = TOP_LEVEL_KEY.exec(rawLine.replace(/\r$/, ""))?.[2] ?? ""
            // The quotes and comment came from the user, so only the value is replaced.
            const {quote, suffix} = splitScalar(afterKey)

            edits.push({
                range: {
                    startLineNumber: line.lineNumber,
                    startColumn: 1,
                    endLineNumber: line.lineNumber,
                    endColumn: model.getLineMaxColumn(line.lineNumber),
                },
                text: `${key}: ${quote}${options.expected.value[key]}${quote}${suffix}`,
            })
        }

        if (!edits.length) return false
        applyCorrection(editor, model, edits)
        return true
    }

    function detach() {
        changeListener?.dispose()
        changeListener = undefined
        decorations?.clear()
        decorations = undefined
        lastValid = undefined
    }

    function attach(editor: monaco.editor.IStandaloneCodeEditor) {
        detach()

        const initial = editor.getModel()?.getValue() ?? ""
        // Left unset by an already-violating buffer — the dead end noted above.
        lastValid = violatedKeys(initial, options.expected.value).length ? undefined : initial
        paint(editor, initial)

        changeListener = editor.onDidChangeModelContent(() => {
            if (correcting) return

            const model = editor.getModel()
            if (!model) return

            const current = model.getValue()

            if (!options.enabled.value) {
                lastValid = current
                return
            }

            const violations = violatedKeys(current, options.expected.value)
            if (!violations.length) {
                lastValid = current
                paint(editor, current)
                return
            }

            correcting = true
            const selection = editor.getSelection()

            // Correcting in place keeps the rest of the edit; the fallback throws it
            // away, which is why only that branch is reported.
            const fallback = restoreLines(editor, model, violations) ? undefined : lastValid

            if (fallback !== undefined) {
                applyCorrection(editor, model, [{
                    range: model.getFullModelRange(),
                    text: fallback,
                    forceMoveMarkers: true,
                }])
            }

            if (selection) editor.setSelection(selection)
            correcting = false

            if (fallback !== undefined) options.onReverted?.(violations)
        })
    }

    // Keyed on the serialised expectations, not on the object: the call site rebuilds
    // that object whenever the flow store replaces the flow, and each new identity
    // would otherwise re-run attach().
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
