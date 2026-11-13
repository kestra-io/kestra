import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {computed, defineComponent, ref, type Ref} from "vue"
import {
    commentOf,
    findReadOnlyLines,
    readTopLevelValue,
    useReadOnlyYamlKeys,
    violatedKeys,
    type ReadOnlyYamlKeysOptions,
} from "./useReadOnlyYamlKeys"

const FLOW = [
    "id: my_flow",
    "namespace: company.team",
    "",
    "tasks:",
    "  - id: hello",
    "    type: io.kestra.plugin.core.log.Log",
    "    message: hello",
    "",
].join("\n")

describe("readTopLevelValue", () => {
    it("reads a top-level scalar", () => {
        expect(readTopLevelValue(FLOW, "id")).toBe("my_flow")
        expect(readTopLevelValue(FLOW, "namespace")).toBe("company.team")
    })

    it("ignores nested keys with the same name", () => {
        // `- id: hello` is a task id; only the unindented one is the flow id.
        expect(readTopLevelValue(FLOW, "id")).not.toBe("hello")
    })

    it("returns undefined for an absent key", () => {
        expect(readTopLevelValue(FLOW, "description")).toBeUndefined()
    })

    it.each([
        ["id: \"quoted\"", "quoted"],
        ["id: 'single'", "single"],
        ["id:    spaced", "spaced"],
        ["id:", ""],
        // An inline comment is not part of the value — reading it as one would
        // make the id permanently unequal to the saved value.
        ["id: my_flow # keep this", "my_flow"],
        ["id: \"my_flow\" # keep this", "my_flow"],
        // `#` only opens a comment when preceded by whitespace.
        ["id: a#b", "a#b"],
    ])("normalises %s", (line, expected) => {
        expect(readTopLevelValue(line, "id")).toBe(expected)
    })

    it("does not read a commented-out key", () => {
        expect(readTopLevelValue("# id: nope\nid: real", "id")).toBe("real")
    })

    it("handles CRLF line endings", () => {
        expect(readTopLevelValue("id: win\r\nnamespace: ns\r\n", "id")).toBe("win")
    })
})

describe("commentOf", () => {
    it.each([
        [" my_flow # keep this", " # keep this"],
        [" \"my_flow\" # keep this", " # keep this"],
        [" 'my_flow'   # spaced", "   # spaced"],
        // Nothing to keep.
        [" my_flow", ""],
        [" \"my_flow\"", ""],
        ["", ""],
        // `#` only opens a comment when preceded by whitespace, so this one is
        // part of the value and must not be mistaken for a suffix.
        [" a#b", ""],
    ])("reads the suffix of %s", (afterKey, expected) => {
        expect(commentOf(afterKey)).toBe(expected)
    })
})

describe("findReadOnlyLines", () => {
    it("locates the requested keys with 1-based line numbers", () => {
        expect(findReadOnlyLines(FLOW, ["id", "namespace"])).toEqual([
            {key: "id", lineNumber: 1},
            {key: "namespace", lineNumber: 2},
        ])
    })

    it("skips keys that are not present", () => {
        expect(findReadOnlyLines("id: only\n", ["id", "namespace"])).toEqual([
            {key: "id", lineNumber: 1},
        ])
    })

    it("reports the first occurrence only", () => {
        expect(findReadOnlyLines("id: first\nid: second\n", ["id"])).toEqual([
            {key: "id", lineNumber: 1},
        ])
    })

    it("finds keys wherever they sit in the document", () => {
        const reordered = "namespace: company.team\ntasks: []\nid: my_flow\n"
        expect(findReadOnlyLines(reordered, ["id"])).toEqual([{key: "id", lineNumber: 3}])
    })
})

describe("violatedKeys", () => {
    const expected = {id: "my_flow", namespace: "company.team"}

    it("reports nothing while the locked values are untouched", () => {
        expect(violatedKeys(FLOW, expected)).toEqual([])
    })

    it("allows edits to the rest of the document", () => {
        const edited = FLOW.replace("message: hello", "message: changed")
        expect(violatedKeys(edited, expected)).toEqual([])
    })

    it("tolerates an inline comment on a locked line", () => {
        const commented = FLOW.replace("id: my_flow", "id: my_flow # do not touch")
        expect(violatedKeys(commented, expected)).toEqual([])
    })

    it("reports a locked value that changed", () => {
        expect(violatedKeys(FLOW.replace("id: my_flow", "id: my_flowX"), expected)).toEqual(["id"])
    })

    it("reports a deleted key", () => {
        expect(violatedKeys(FLOW.replace("id: my_flow\n", ""), expected)).toEqual(["id"])
    })

    it("reports both keys at once", () => {
        expect(violatedKeys("id: a\nnamespace: b\n", expected)).toEqual(["id", "namespace"])
    })

    it("never fails on an expectation that is not known yet", () => {
        // Creation: nothing is saved server-side, so nothing is locked.
        expect(violatedKeys(FLOW, {id: undefined, namespace: undefined})).toEqual([])
    })

    it("accepts a re-quoted but unchanged value", () => {
        expect(violatedKeys("id: \"my_flow\"\nnamespace: company.team\n", expected)).toEqual([])
    })
})

type Selection = {anchor: number; head: number}
type UserChange = {offset: number; length: number; text: string}
type PaintedDecoration = {range: {startLineNumber: number; endLineNumber: number}}
type RangeEdit = {
    range: {startLineNumber: number; startColumn: number; endLineNumber: number; endColumn: number}
    text: string
}
type ContentEvent = {
    changes: {rangeOffset: number; rangeLength: number; text: string}[]
    isFlush: boolean
    isUndoing: boolean
}

function editorDouble(initial: string) {
    let text = initial
    let selections: Selection[] = [{anchor: 0, head: 0}]
    let painted: PaintedDecoration[] = []
    let registrations = 0
    const contentListeners: Array<(event: ContentEvent) => void> = []
    const selectionListeners: Array<() => void> = []
    const modelListeners: Array<() => void> = []
    const undoStack: Array<{text: string; selections: Selection[]}> = []

    function lineStarts(source: string) {
        const starts = [0]
        for (let index = 0; index < source.length; index++) {
            if (source[index] === "\n") starts.push(index + 1)
        }
        return starts
    }

    function lineOf(offset: number) {
        return lineStarts(text.slice(0, offset)).length
    }

    function offsetOf(line: number, column: number) {
        return lineStarts(text)[line - 1] + column - 1
    }

    function collapse(start: number, end: number) {
        const first = lineOf(start)
        const last = lineOf(end)
        for (const decoration of painted) {
            const line = decoration.range.startLineNumber
            if (line >= first && line <= last) {
                decoration.range.startLineNumber = first
                decoration.range.endLineNumber = first
            }
        }
    }

    function applyChanges(changes: UserChange[]) {
        const descending = [...changes].sort((a, b) => b.offset - a.offset)
        for (const change of descending) {
            collapse(change.offset, change.offset + change.length)
            text = text.slice(0, change.offset) + change.text + text.slice(change.offset + change.length)
        }
        return [...changes]
            .sort((a, b) => a.offset - b.offset)
            .map((change) => ({rangeOffset: change.offset, rangeLength: change.length, text: change.text}))
    }

    function notifyContent(event: ContentEvent) {
        contentListeners.forEach((listener) => listener(event))
    }

    function notifySelection() {
        selectionListeners.forEach((listener) => listener())
    }

    const model = {
        getValue: () => text,
        getPositionAt(offset: number) {
            const before = text.slice(0, offset)
            return {lineNumber: lineStarts(before).length, column: offset - before.lastIndexOf("\n")}
        },
        pushEditOperations(_before: unknown, edits: RangeEdit[]) {
            const changes: UserChange[] = edits.map((edit) => {
                const start = offsetOf(edit.range.startLineNumber, edit.range.startColumn)
                const end = offsetOf(edit.range.endLineNumber, edit.range.endColumn)
                return {offset: start, length: end - start, text: edit.text}
            })
            const reported = applyChanges(changes)
            const last = reported[reported.length - 1]
            const caret = last.rangeOffset + last.text.length
            selections = [{anchor: caret, head: caret}]
            notifyContent({changes: reported, isFlush: false, isUndoing: false})
            return null
        },
    }

    const editor = {
        getModel: () => model,
        getSelections: () => selections,
        setSelections(next: Selection[]) {
            selections = next
            notifySelection()
        },
        onDidChangeModelContent(callback: (event: ContentEvent) => void) {
            registrations += 1
            contentListeners.push(callback)
            return {dispose: () => contentListeners.splice(contentListeners.indexOf(callback), 1)}
        },
        onDidChangeCursorSelection(callback: () => void) {
            selectionListeners.push(callback)
            return {dispose: () => selectionListeners.splice(selectionListeners.indexOf(callback), 1)}
        },
        onDidChangeModel(callback: () => void) {
            modelListeners.push(callback)
            return {dispose: () => modelListeners.splice(modelListeners.indexOf(callback), 1)}
        },
        createDecorationsCollection: () => ({
            set: (next: PaintedDecoration[]) => { painted = next },
            clear: () => { painted = [] },
        }),
        executeEdits(_source: string, edits: RangeEdit[]) {
            undoStack.push({text, selections})
            model.pushEditOperations(null, edits)
            return true
        },
    }

    return {
        editor,
        current: () => text,
        selections: () => selections,
        decorationCount: () => painted.length,
        decoratedLines: () => painted.map((decoration) => decoration.range.startLineNumber),
        undoDepth: () => undoStack.length,
        attachCount: () => registrations,
        select(next: Selection[]) {
            selections = next
            notifySelection()
        },
        edit(changes: UserChange[]) {
            undoStack.push({text, selections})
            const reported = applyChanges(changes)
            let shift = 0
            selections = reported.map((change) => {
                const caret = change.rangeOffset + shift + change.text.length
                shift += change.text.length - change.rangeLength
                return {anchor: caret, head: caret}
            })
            notifyContent({changes: reported, isFlush: false, isUndoing: false})
            notifySelection()
        },
        setValue(next: string) {
            const previousLength = text.length
            text = next
            selections = [{anchor: 0, head: 0}]
            notifyContent({
                changes: [{rangeOffset: 0, rangeLength: previousLength, text: next}],
                isFlush: true,
                isUndoing: false,
            })
            notifySelection()
        },
        swapModel(next: string) {
            text = next
            selections = [{anchor: 0, head: 0}]
            modelListeners.forEach((listener) => listener())
        },
        undo() {
            const element = undoStack.pop()
            if (!element) return
            const previousLength = text.length
            text = element.text
            selections = element.selections
            notifyContent({
                changes: [{rangeOffset: 0, rangeLength: previousLength, text: element.text}],
                isFlush: false,
                isUndoing: true,
            })
        },
    }
}

function withComposable(run: () => void) {
    return mount(defineComponent({
        setup() {
            run()
            return () => null
        },
    }))
}

const caretAt = (offset: number): Selection => ({anchor: offset, head: offset})
const insertAt = (offset: number, text: string): UserChange => ({offset, length: 0, text})
const endOfLine = (needle: string) => FLOW.indexOf("\n", FLOW.indexOf(needle))

describe("useReadOnlyYamlKeys", () => {
    function guardWith(
        double: ReturnType<typeof editorDouble>,
        expected: Ref<Record<string, string | undefined>>,
        enabled = true,
    ) {
        return withComposable(() => useReadOnlyYamlKeys({
            editor: ref(double.editor) as unknown as ReadOnlyYamlKeysOptions["editor"],
            expected,
            enabled: computed(() => enabled),
        }))
    }

    function guard(double: ReturnType<typeof editorDouble>, enabled = true) {
        return guardWith(double, ref({id: "my_flow", namespace: "company.team"}), enabled)
    }

    const message = FLOW.indexOf("hello", FLOW.indexOf("message"))
    const namespaceLine = FLOW.indexOf("namespace")

    it("refuses an edit to a locked value", () => {
        const double = editorDouble(FLOW)
        guard(double)

        double.edit([insertAt(endOfLine("id: my_flow"), "X")])

        expect(double.current()).toBe(FLOW)
    })

    it("refuses typing in front of a locked key and keeps the cursor where it was", () => {
        const double = editorDouble(FLOW)
        guard(double)
        double.select([caretAt(0)])

        double.edit([insertAt(0, "x")])

        expect(double.current()).toBe(FLOW)
        expect(double.selections()).toEqual([caretAt(0)])
    })

    it("refuses a line join that would swallow a locked key", () => {
        const double = editorDouble(FLOW)
        guard(double)
        double.select([caretAt(namespaceLine)])

        double.edit([{offset: namespaceLine - 1, length: 1, text: ""}])

        expect(double.current()).toBe(FLOW)
        expect(double.selections()).toEqual([caretAt(namespaceLine)])
    })

    it("refuses deleting a locked line and keeps the selection", () => {
        const double = editorDouble(FLOW)
        guard(double)
        const selection = {anchor: 0, head: namespaceLine}
        double.select([selection])

        double.edit([{offset: 0, length: namespaceLine, text: ""}])

        expect(double.current()).toBe(FLOW)
        expect(double.selections()).toEqual([selection])
    })

    it("refuses a paste over the whole document", () => {
        const double = editorDouble(FLOW)
        guard(double)
        const selection = {anchor: 0, head: FLOW.length}
        double.select([selection])

        double.edit([{offset: 0, length: FLOW.length, text: "tasks: []"}])

        expect(double.current()).toBe(FLOW)
        expect(double.selections()).toEqual([selection])
    })

    it("refuses every cursor's edit when one of them touches a locked line", () => {
        const double = editorDouble(FLOW)
        guard(double)
        const carets = [caretAt(0), caretAt(message)]
        double.select(carets)

        double.edit([insertAt(0, "x"), insertAt(message, "x")])

        expect(double.current()).toBe(FLOW)
        expect(double.selections()).toEqual(carets)
    })

    it("applies a multi-cursor edit that touches no locked line", () => {
        const double = editorDouble(FLOW)
        guard(double)

        double.edit([insertAt(message, "x"), insertAt(message + 5, "y")])

        expect(double.current()).toBe(FLOW.replace("message: hello", "message: xhelloy"))
    })

    it("lets edits to the rest of the document through and leaves the cursor to the editor", () => {
        const double = editorDouble(FLOW)
        guard(double)

        double.edit([insertAt(message, "x")])

        expect(double.current()).toBe(FLOW.replace("message: hello", "message: xhello"))
        expect(double.selections()).toEqual([caretAt(message + 1)])
    })

    it("allows Enter at the end of a locked line", () => {
        const double = editorDouble(FLOW)
        guard(double)

        double.edit([insertAt(endOfLine("id: my_flow"), "\n")])

        expect(double.current()).toBe(FLOW.replace("id: my_flow\n", "id: my_flow\n\n"))
    })

    it("allows an inline comment on a locked line", () => {
        const double = editorDouble(FLOW)
        guard(double)

        double.edit([insertAt(endOfLine("id: my_flow"), " # note")])

        expect(double.current()).toBe(FLOW.replace("id: my_flow", "id: my_flow # note"))
    })

    it("keeps the document intact when the user keeps typing on a locked line", () => {
        const double = editorDouble(FLOW)
        guard(double)

        double.edit([insertAt(0, "x")])
        double.edit([insertAt(0, "y")])

        expect(double.current()).toBe(FLOW)
    })

    it("refuses against the content that followed the last accepted edit", () => {
        const double = editorDouble(FLOW)
        guard(double)
        const edited = FLOW.replace("message: hello", "message: hello world")
        double.edit([insertAt(message + 5, " world")])

        double.edit([insertAt(0, "x")])

        expect(double.current()).toBe(edited)
    })

    it("accepts a programmatic replacement and refuses against it afterwards", () => {
        const double = editorDouble(FLOW)
        guard(double)
        const reloaded = FLOW.replace("message: hello", "message: reloaded")

        double.setValue(reloaded)
        double.edit([insertAt(0, "x")])

        expect(double.current()).toBe(reloaded)
    })

    it("accepts a programmatic replacement that does not match the expectations", () => {
        const double = editorDouble(FLOW)
        guard(double)
        const other = "id: other_flow\nnamespace: other.team\n\ntasks: []\n"

        double.setValue(other)

        expect(double.current()).toBe(other)
    })

    it("still allows edits when the buffer already violated as the guard attached", () => {
        const violating = FLOW.replace("id: my_flow", "id: renamed")
        const double = editorDouble(violating)
        guard(double)

        double.edit([insertAt(violating.indexOf("hello", violating.indexOf("message")), "x")])

        expect(double.current()).toBe(violating.replace("message: hello", "message: xhello"))
    })

    it("refuses a new violation in a buffer that already violated another key", () => {
        const violating = FLOW.replace("id: my_flow", "id: renamed")
        const double = editorDouble(violating)
        guard(double)

        double.edit([insertAt(violating.indexOf("namespace"), "x")])

        expect(double.current()).toBe(violating)
    })

    it("computes the inversion against the new document after the model is swapped", () => {
        const double = editorDouble(FLOW)
        guard(double)
        const swapped = FLOW.replace("message: hello", "message: swapped, and now longer")

        double.swapModel(swapped)
        double.edit([insertAt(0, "x")])

        expect(double.current()).toBe(swapped)
    })

    it("stays out of the way while disabled, as when creating", () => {
        const double = editorDouble(FLOW)
        guard(double, false)

        double.edit([insertAt(0, "x")])

        expect(double.current()).toBe(`x${FLOW}`)
    })

    it("decorates both locked lines", () => {
        const double = editorDouble(FLOW)
        guard(double)

        expect(double.decorationCount()).toBe(2)
    })

    it("decorates nothing while disabled", () => {
        const double = editorDouble(FLOW)
        guard(double, false)

        expect(double.decorationCount()).toBe(0)
    })

    it("repaints the locked lines after refusing a line join", () => {
        const double = editorDouble(FLOW)
        guard(double)

        double.edit([{offset: namespaceLine - 1, length: 1, text: ""}])

        expect(double.decoratedLines()).toEqual([1, 2])
    })

    it("leaves undo working after a keystroke is refused", () => {
        const double = editorDouble(FLOW)
        guard(double)
        const edited = FLOW.replace("message: hello", "message: xhello")
        double.edit([insertAt(message, "x")])

        double.edit([insertAt(0, "x")])
        expect(double.current()).toBe(edited)

        double.undo()
        expect(double.current()).toBe(edited)

        double.undo()
        expect(double.current()).toBe(FLOW)
    })

    it("does not grow the undo stack for a correction", () => {
        const double = editorDouble(FLOW)
        guard(double)

        const before = double.undoDepth()
        double.edit([insertAt(0, "x")])

        expect(double.undoDepth()).toBe(before + 1)
    })

    it("does not re-attach when the expectations are replaced by an equal object", async () => {
        const double = editorDouble(FLOW)
        const expected = ref<Record<string, string | undefined>>({id: "my_flow", namespace: "company.team"})
        guardWith(double, expected)
        const attached = double.attachCount()

        expected.value = {id: "my_flow", namespace: "company.team"}
        await new Promise((resolve) => setTimeout(resolve))

        expect(double.attachCount()).toBe(attached)
    })

    it("accepts a whole-document replacement when the expectations change with it", async () => {
        const double = editorDouble(FLOW)
        const expected = ref<Record<string, string | undefined>>({id: "my_flow", namespace: "company.team"})
        guardWith(double, expected)
        const other = "id: other_flow\nnamespace: other.team\n\ntasks: []\n"

        expected.value = {id: "other_flow", namespace: "other.team"}
        await new Promise((resolve) => setTimeout(resolve))
        double.edit([{offset: 0, length: FLOW.length, text: other}])

        expect(double.current()).toBe(other)
    })

    it("does re-attach when a locked value actually changes", async () => {
        const double = editorDouble(FLOW)
        const expected = ref<Record<string, string | undefined>>({id: "my_flow", namespace: "company.team"})
        guardWith(double, expected)
        const attached = double.attachCount()

        expected.value = {id: "renamed_flow", namespace: "company.team"}
        await new Promise((resolve) => setTimeout(resolve))

        expect(double.attachCount()).toBe(attached + 1)
    })
})
