import {onBeforeUnmount, watch, type Ref} from "vue"
import type * as monaco from "monaco-editor/editor/editor.api"

/**
 * Matches a top-level YAML key, i.e. one written at column 0.
 *
 * Anchoring at column 0 is what makes this safe without a full parse: YAML
 * block-scalar content is always indented, so an unindented `id:` can never be
 * part of a multi-line string value, and `# id: x` is not matched at all.
 */
const TOP_LEVEL_KEY = /^([A-Za-z0-9_-]+)[ \t]*:(.*)$/

/** The class placed on each locked line; styled by the consuming component. */
export const READ_ONLY_LINE_CLASS = "ks-readonly-yaml-line"

export interface ReadOnlyKeyLine {
    key: string
    /** 1-based, matching Monaco's line numbering. */
    lineNumber: number
}

/**
 * The scalar carried by everything after `key:`.
 *
 * Quotes and inline comments are stripped, because neither changes the value:
 * treating `id: my-flow # note` as the literal `my-flow # note` would make it
 * permanently unequal to the saved id, and every later keystroke would then read
 * as an attempt to change it.
 */
function scalarOf(raw: string): string {
    const value = raw.trim()
    const quote = value[0]

    if (quote === "\"" || quote === "'") {
        const closing = value.indexOf(quote, 1)
        // A quote still being typed has no closing partner yet.
        return closing === -1 ? value.slice(1) : value.slice(1, closing)
    }

    // YAML only opens an inline comment on a `#` preceded by whitespace, so a
    // `#` inside a bare scalar (`id: a#b`) is part of the value.
    const comment = value.search(/\s#/)
    return (comment === -1 ? value : value.slice(0, comment)).trim()
}

/**
 * Read the scalar value of a top-level key, or undefined when the key is absent.
 *
 * Only the first occurrence counts — a duplicate top-level key is invalid YAML,
 * and the parser would reject the document long before this matters.
 */
export function readTopLevelValue(source: string, key: string): string | undefined {
    for (const rawLine of source.split("\n")) {
        const match = TOP_LEVEL_KEY.exec(rawLine.replace(/\r$/, ""))
        if (match?.[1] !== key) continue
        return scalarOf(match[2])
    }
    return undefined
}

/**
 * Locate the lines holding the given keys, so they can be decorated as locked.
 * Keys missing from the document are simply not returned.
 */
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

/**
 * The keys whose value in `source` no longer matches the saved resource.
 *
 * Comparing against the expected values — rather than against the previous
 * revision of the text — is what keeps a legitimate whole-document replacement
 * (loading another flow, restoring a revision) from looking like a violation:
 * there, source and expectations change together.
 *
 * An expectation of `undefined` means "not known yet", and never fails.
 */
export function violatedKeys(
    source: string,
    expected: Record<string, string | undefined>,
): string[] {
    return Object.entries(expected)
        .filter(([key, value]) => {
            if (value === undefined) return false
            const actual = readTopLevelValue(source, key)
            // A key the user has not finished retyping yet is still a violation;
            // only an unchanged value is accepted.
            return actual !== value
        })
        .map(([key]) => key)
}

export interface ReadOnlyYamlKeysOptions {
    /**
     * The Monaco editor to guard; undefined until it mounts.
     *
     * Must be a shallowRef: a plain ref() would wrap the editor in a deep
     * reactive proxy and break it.
     */
    editor: Ref<monaco.editor.IStandaloneCodeEditor | undefined>
    /** Expected value per locked key. Undefined values disable the guard for that key. */
    expected: Ref<Record<string, string | undefined>>
    /** Guard only applies while true — creation flows stay fully editable. */
    enabled: Ref<boolean>
    /** Tooltip shown when hovering a locked line. */
    hoverMessage?: Ref<string | undefined>
}

/**
 * Keep the given top-level YAML keys read-only inside a Monaco editor.
 *
 * Monaco has no native read-only ranges, so the guard undoes an offending change
 * on the same tick it arrives. That is invisible to the user — the character
 * never appears — unlike correcting it after a debounce, which shows the edit
 * being accepted and then taken away.
 *
 * Only the locked lines are rewritten, so an edit that spans them (select-all
 * and paste, find-and-replace) keeps everything else the user did.
 *
 * Locked lines are only decorated, never made unselectable, so their values stay
 * selectable and copyable.
 */
export function useReadOnlyYamlKeys(options: ReadOnlyYamlKeysOptions) {
    let changeListener: monaco.IDisposable | undefined
    let decorations: monaco.editor.IEditorDecorationsCollection | undefined
    /** Last content known to satisfy every expectation; the last-resort fallback. */
    let lastValid: string | undefined
    /** Guards against reacting to our own corrective edit. */
    let correcting = false

    function keys(): string[] {
        return Object.keys(options.expected.value)
    }

    /**
     * Apply the correction as part of the edit that provoked it.
     *
     * By the time `onDidChangeModelContent` runs, Monaco has already closed the
     * undo element for the user's keystroke. `executeEdits` would open a second
     * one, and undoing that would put the violating text back, re-enter this
     * listener and correct again — leaving the undo stack unable to step past
     * the pair, and the user's earlier work unreachable for the rest of the
     * session. `pushEditOperations` with no intervening `pushStackElement()`
     * merges into the element already open, so one undo reverts both halves and
     * lands on a document that violates nothing.
     */
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

    /**
     * Put the saved value back on each offending line, leaving the rest of the
     * document as the user left it. Returns false when a locked key is no longer
     * present at all, which cannot be corrected line-by-line.
     */
    function restoreLines(
        editor: monaco.editor.IStandaloneCodeEditor,
        model: monaco.editor.ITextModel,
        violations: string[],
    ): boolean {
        const source = model.getValue()
        const edits: monaco.editor.IIdentifiedSingleEditOperation[] = []

        for (const key of violations) {
            const line = findReadOnlyLines(source, [key])[0]
            if (!line) return false
            edits.push({
                range: {
                    startLineNumber: line.lineNumber,
                    startColumn: 1,
                    endLineNumber: line.lineNumber,
                    endColumn: model.getLineMaxColumn(line.lineNumber),
                },
                text: `${key}: ${options.expected.value[key]}`,
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

            // Whole-document fallback only when a locked key was removed outright;
            // otherwise the user's other changes are preserved.
            if (!restoreLines(editor, model, violations) && lastValid !== undefined) {
                applyCorrection(editor, model, [{
                    range: model.getFullModelRange(),
                    text: lastValid,
                    forceMoveMarkers: true,
                }])
            }

            if (selection) editor.setSelection(selection)
            correcting = false

            lastValid = model.getValue()
            paint(editor, lastValid)
        })
    }

    // Identity comparison only. A deep watch here would walk the Monaco editor
    // object graph, which is both enormous and self-referential; `expected` is a
    // computed whose identity already changes exactly when the locked values do.
    watch(
        [options.editor, options.enabled, options.expected],
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
