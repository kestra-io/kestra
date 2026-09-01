import {onBeforeUnmount, watch, type Ref} from "vue"
import type * as monaco from "monaco-editor/editor/editor.api"

// Anchored at column 0: YAML block-scalar content is always indented, so an
// unindented `id:` can never be part of a multi-line string value.
const TOP_LEVEL_KEY = /^([A-Za-z0-9_-]+)[ \t]*:(.*)$/

/** The class placed on each locked line; styled by the consuming component. */
export const READ_ONLY_LINE_CLASS = "ks-readonly-yaml-line"

export interface ReadOnlyKeyLine {
    key: string
    /** 1-based, matching Monaco's line numbering. */
    lineNumber: number
}

// Quotes and inline comments are stripped from the value because neither changes
// it, and kept as the suffix so a correction does not discard them.
function splitScalar(raw: string): {value: string; suffix: string} {
    const trimmed = raw.trim()
    const quote = trimmed[0]

    if (quote === "\"" || quote === "'") {
        const closing = trimmed.indexOf(quote, 1)
        // A quote still being typed has no closing partner yet.
        return closing === -1
            ? {value: trimmed.slice(1), suffix: ""}
            : {value: trimmed.slice(1, closing), suffix: trimmed.slice(closing + 1)}
    }

    // YAML only opens an inline comment on a `#` preceded by whitespace, so a
    // `#` inside a bare scalar (`id: a#b`) is part of the value.
    const comment = trimmed.search(/\s#/)
    return comment === -1
        ? {value: trimmed, suffix: ""}
        : {value: trimmed.slice(0, comment).trim(), suffix: trimmed.slice(comment)}
}

function scalarOf(raw: string): string {
    return splitScalar(raw).value
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
        return scalarOf(match[2])
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

// Compared against the expected values rather than the previous text, so that a
// legitimate whole-document replacement — another flow, a restored revision,
// where source and expectations change together — is not read as a violation.
// An expectation of `undefined` means "not known yet" and never fails.
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
    /**
     * Called with the offending keys when a change was reverted wholesale rather
     * than corrected in place, which is the only case where the user loses work
     * and so the only one worth reporting.
     */
    onReverted?: (keys: string[]) => void
}

/**
 * Keep the given top-level YAML keys read-only inside a Monaco editor.
 *
 * Monaco ships no read-only-range API of its own, so the guard undoes an
 * offending change on the tick it arrives — the character never appears, unlike
 * correcting after a debounce, which shows the edit landing and then being taken
 * away. Only the locked lines are rewritten, so an edit spanning them keeps
 * everything else. Locked lines are decorated but never made unselectable, so
 * their values stay selectable and copyable.
 */
export function useReadOnlyYamlKeys(options: ReadOnlyYamlKeysOptions) {
    let changeListener: monaco.IDisposable | undefined
    let decorations: monaco.editor.IEditorDecorationsCollection | undefined
    /** Last content known to satisfy every expectation; the whole-document fallback. */
    let lastValid: string | undefined
    /** Guards against reacting to our own corrective edit. */
    let correcting = false

    function keys(): string[] {
        return Object.keys(options.expected.value)
    }

    // pushEditOperations with no intervening pushStackElement() joins the undo
    // element Monaco still has open for the user's keystroke, so one undo reverts
    // both halves. executeEdits would open a second element, and undoing that
    // would restore the violating text and re-enter this listener.
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

    // Returns false when a locked key is absent altogether, which cannot be
    // corrected line by line. Anything trailing the value is carried over.
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

            edits.push({
                range: {
                    startLineNumber: line.lineNumber,
                    startColumn: 1,
                    endLineNumber: line.lineNumber,
                    endColumn: model.getLineMaxColumn(line.lineNumber),
                },
                text: `${key}: ${options.expected.value[key]}${commentOf(afterKey)}`,
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
        // Known dead end: a buffer that already violates leaves lastValid unset,
        // so a later edit removing a locked key outright cannot be recovered and
        // passes unreported. It resolves itself on the next clean edit.
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

            // Correcting the lines in place keeps the rest of the edit; the
            // whole-document fallback does not, which is why only that branch is
            // reported below.
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

    // Keyed on the serialised expectations, not on `expected` itself: the call
    // site rebuilds that object whenever the flow store replaces the flow, which
    // is far more often than the locked values change, and each new identity
    // would re-run attach(). This is the deep-watch / computed-spread trap in
    // ui/AGENTS.md; a deep watch would walk the Monaco editor's object graph.
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
