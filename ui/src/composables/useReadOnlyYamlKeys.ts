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
 * Read the scalar value of a top-level key, or undefined when the key is absent.
 *
 * Only the first occurrence counts — a duplicate top-level key is invalid YAML,
 * and the parser would reject the document long before this matters.
 */
export function readTopLevelValue(source: string, key: string): string | undefined {
    for (const rawLine of source.split("\n")) {
        const match = TOP_LEVEL_KEY.exec(rawLine.replace(/\r$/, ""))
        if (match?.[1] !== key) continue
        return unquote(match[2].trim())
    }
    return undefined
}

function unquote(value: string): string {
    const quote = value[0]
    if ((quote === "\"" || quote === "'") && value.length > 1 && value.endsWith(quote)) {
        return value.slice(1, -1)
    }
    return value
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
    /** The Monaco editor to guard; undefined until it mounts. */
    editor: Ref<monaco.editor.IStandaloneCodeEditor | undefined>
    /** Expected value per locked key. Empty/undefined values disable the guard for that key. */
    expected: Ref<Record<string, string | undefined>>
    /** Guard only applies while true — creation flows stay fully editable. */
    enabled: Ref<boolean>
    /** Tooltip shown when hovering a locked line. */
    hoverMessage?: Ref<string | undefined>
}

/**
 * Keep the given top-level YAML keys read-only inside a Monaco editor.
 *
 * Monaco has no native read-only ranges, so the guard reverts an offending
 * change on the same tick it arrives. That is invisible to the user — the
 * character never appears — unlike reverting after a debounce, which shows the
 * edit being accepted and then taken away.
 *
 * Locked lines are only decorated, never made unselectable, so their values
 * stay selectable and copyable.
 */
export function useReadOnlyYamlKeys(options: ReadOnlyYamlKeysOptions) {
    let changeListener: monaco.IDisposable | undefined
    let decorations: monaco.editor.IEditorDecorationsCollection | undefined
    /** Last content known to satisfy every expectation; the target of a revert. */
    let lastValid: string | undefined
    /** Guards against reacting to our own revert edit. */
    let reverting = false

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
            if (reverting) return

            const model = editor.getModel()
            if (!model) return

            const current = model.getValue()

            if (!options.enabled.value) {
                lastValid = current
                return
            }

            // Nothing to revert to yet (the document arrived already inconsistent):
            // accept the edit rather than trapping the user in a broken state.
            if (violatedKeys(current, options.expected.value).length && lastValid !== undefined) {
                reverting = true
                const selection = editor.getSelection()
                editor.executeEdits("readonly-yaml-keys", [{
                    range: model.getFullModelRange(),
                    text: lastValid,
                    forceMoveMarkers: true,
                }])
                if (selection) editor.setSelection(selection)
                reverting = false

                paint(editor, lastValid)
                return
            }

            lastValid = current
            paint(editor, current)
        })
    }

    watch(
        [options.editor, options.enabled, options.expected],
        ([editor]) => {
            if (!editor) {
                detach()
                return
            }
            attach(editor)
        },
        {immediate: true, deep: true},
    )

    onBeforeUnmount(detach)

    return {detach}
}
