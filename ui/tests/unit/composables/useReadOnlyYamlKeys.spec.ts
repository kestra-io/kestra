import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {computed, defineComponent, ref, type Ref} from "vue"
import {
    commentOf,
    findReadOnlyLines,
    readTopLevelValue,
    useReadOnlyYamlKeys,
    violatedKeys,
} from "../../../src/composables/useReadOnlyYamlKeys"

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

    it("reports a changed id", () => {
        expect(violatedKeys(FLOW.replace("id: my_flow", "id: my_flowX"), expected)).toEqual(["id"])
    })

    it("reports a changed namespace", () => {
        expect(violatedKeys(FLOW.replace("company.team", "other.team"), expected)).toEqual(["namespace"])
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

/**
 * Minimal stand-in for the slice of Monaco the composable touches. Edits are
 * applied by line range, which is what makes the "keep the rest of the edit"
 * behaviour observable.
 *
 * The undo stack is modelled because the guard's correctness depends on it: an
 * edit made with `pushEditOperations` joins the element already open, while one
 * made with `executeEdits` opens a new element of its own. Undo pops one element
 * and, like Monaco, notifies the content listeners afterwards.
 *
 * Listener registrations are counted rather than merely tracked, so that a guard
 * re-attaching when it should not is visible even though attach() leaves exactly
 * one listener behind either way.
 */
function editorDouble(initial: string) {
    let lines = initial.split("\n")
    const listeners: Array<() => void> = []
    let painted: unknown[] = []
    let registrations = 0
    /** Snapshot taken before each undo element; last entry is the newest. */
    const undoStack: string[] = []

    const model = {
        getValue: () => lines.join("\n"),
        getFullModelRange: () => ({
            startLineNumber: 1,
            startColumn: 1,
            endLineNumber: lines.length,
            endColumn: (lines[lines.length - 1]?.length ?? 0) + 1,
        }),
        getLineMaxColumn: (line: number) => (lines[line - 1]?.length ?? 0) + 1,
        /** Joins the element already open — no new undo step. */
        pushEditOperations(
            _before: unknown,
            edits: {range: {startLineNumber: number; endLineNumber: number}; text: string}[],
        ) {
            applyEdits(edits)
            return null
        },
    }

    function applyEdits(edits: {range: {startLineNumber: number; endLineNumber: number}; text: string}[]) {
        // Descending, so earlier line numbers stay valid as we splice.
        for (const edit of [...edits].sort((a, b) => b.range.startLineNumber - a.range.startLineNumber)) {
            const start = edit.range.startLineNumber - 1
            const count = edit.range.endLineNumber - edit.range.startLineNumber + 1
            lines.splice(start, count, ...edit.text.split("\n"))
        }
        // Monaco notifies synchronously, which is also what exercises the
        // composable's re-entrancy guard.
        listeners.forEach((listener) => listener())
    }

    const editor = {
        getModel: () => model,
        onDidChangeModelContent(callback: () => void) {
            registrations += 1
            listeners.push(callback)
            return {dispose: () => listeners.splice(listeners.indexOf(callback), 1)}
        },
        createDecorationsCollection: () => ({
            set: (next: unknown[]) => { painted = next },
            clear: () => { painted = [] },
        }),
        /** Opens its own undo element, as Monaco does outside an open one. */
        executeEdits(_source: string, edits: {range: {startLineNumber: number; endLineNumber: number}; text: string}[]) {
            undoStack.push(lines.join("\n"))
            applyEdits(edits)
            return true
        },
        getSelection: () => null,
        getSelections: () => [],
        setSelection: () => {},
    }

    return {
        editor,
        current: () => lines.join("\n"),
        decorationCount: () => painted.length,
        undoDepth: () => undoStack.length,
        attachCount: () => registrations,
        /** Simulate the user changing the buffer; each one opens an undo element. */
        type(next: string) {
            undoStack.push(lines.join("\n"))
            lines = next.split("\n")
            listeners.forEach((listener) => listener())
        },
        /** Pop one undo element, then notify — the order Monaco uses. */
        undo() {
            const previous = undoStack.pop()
            if (previous === undefined) return
            lines = previous.split("\n")
            listeners.forEach((listener) => listener())
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

describe("useReadOnlyYamlKeys", () => {
    function guardWith(
        double: ReturnType<typeof editorDouble>,
        expected: Ref<Record<string, string | undefined>>,
        enabled = true,
        onReverted?: (keys: string[]) => void,
    ) {
        return withComposable(() => useReadOnlyYamlKeys({
            editor: ref(double.editor) as any,
            expected,
            enabled: computed(() => enabled),
            onReverted,
        }))
    }

    function guard(double: ReturnType<typeof editorDouble>, enabled = true) {
        return guardWith(double, ref({id: "my_flow", namespace: "company.team"}), enabled)
    }

    /** Guard as usual, handing back the spy that stands in for the toast. */
    function guardReporting(double: ReturnType<typeof editorDouble>) {
        const reverted = vi.fn()
        guardWith(double, ref({id: "my_flow", namespace: "company.team"}), true, reverted)
        return reverted
    }

    it("rejects an edit to a locked value", () => {
        const double = editorDouble(FLOW)
        guard(double)

        double.type(FLOW.replace("id: my_flow", "id: my_flowX"))

        expect(double.current()).toBe(FLOW)
    })

    it("lets edits to the rest of the document through", () => {
        const double = editorDouble(FLOW)
        guard(double)

        const edited = FLOW.replace("message: hello", "message: changed")
        double.type(edited)

        expect(double.current()).toBe(edited)
    })

    it("keeps the rest of a paste that also changed a locked line", () => {
        const double = editorDouble(FLOW)
        guard(double)

        // Select-all and paste a different flow: the id must snap back, but the
        // pasted body must survive — discarding it silently is the failure mode.
        double.type([
            "id: someone_elses_flow",
            "namespace: company.team",
            "",
            "tasks:",
            "  - id: pasted",
            "    type: io.kestra.plugin.core.log.Log",
            "    message: pasted",
            "",
        ].join("\n"))

        expect(double.current()).toContain("id: my_flow")
        expect(double.current()).not.toContain("someone_elses_flow")
        expect(double.current()).toContain("  - id: pasted")
        expect(double.current()).toContain("message: pasted")
    })

    it("restores both locked lines when a paste changes each of them", () => {
        const double = editorDouble(FLOW)
        guard(double)

        double.type("id: other\nnamespace: other.team\n\ntasks: []\n")

        expect(double.current()).toContain("id: my_flow")
        expect(double.current()).toContain("namespace: company.team")
        expect(double.current()).toContain("tasks: []")
    })

    it("falls back to the last good document when a locked key is removed", () => {
        const double = editorDouble(FLOW)
        guard(double)

        double.type(FLOW.replace("id: my_flow\n", ""))

        expect(double.current()).toBe(FLOW)
    })

    it("reports the paste it threw away when a locked key is dropped", () => {
        const double = editorDouble(FLOW)
        const reverted = guardReporting(double)

        // A snippet pasted over the whole document that simply carries no `id:`
        // line — nothing is left to correct, so the paste goes in full. That is
        // the one case where the user loses work and has to be told.
        double.type([
            "namespace: company.team",
            "",
            "tasks:",
            "  - id: pasted",
            "    type: io.kestra.plugin.core.log.Log",
            "",
        ].join("\n"))

        expect(double.current()).toBe(FLOW)
        expect(reverted).toHaveBeenCalledWith(["id"])
    })

    it("says nothing when the locked line was corrected in place", () => {
        const double = editorDouble(FLOW)
        const reverted = guardReporting(double)

        // The id snaps back and the rest of the edit survives, so nothing was
        // lost — reporting here would fire on every keystroke on a locked line.
        double.type(FLOW.replace("id: my_flow", "id: someone_elses_flow"))

        expect(double.current()).toBe(FLOW)
        expect(reverted).not.toHaveBeenCalled()
    })

    it("says nothing when there is no good document to fall back to", () => {
        // Attached to a buffer that already violates, so nothing was recorded to
        // restore: the edit stands, and no revert happened to report.
        const stale = FLOW.replace("id: my_flow", "id: stale")
        const double = editorDouble(stale)
        const reverted = guardReporting(double)

        double.type(stale.replace("id: stale\n", ""))

        expect(reverted).not.toHaveBeenCalled()
    })

    it("keeps an inline comment when correcting the line it sits on", () => {
        const commented = FLOW.replace("id: my_flow", "id: my_flow # keep")
        const double = editorDouble(commented)
        guard(double)

        // Typing on the locked line itself, which is what reaches restoreLines —
        // an edit elsewhere leaves violatedKeys empty and never gets there.
        double.type(commented.replace("id: my_flow # keep", "id: my_flowX # keep"))

        // The value snaps back and the comment, which was the user's to write,
        // survives with it.
        expect(double.current()).toBe(commented)
    })

    it("keeps a comment on a locked line that a paste rewrote wholesale", () => {
        const commented = FLOW.replace("id: my_flow", "id: my_flow # keep")
        const double = editorDouble(commented)
        guard(double)

        // The pasted line carries its own comment; the value is refused but the
        // comment that arrived with it is what the line now legitimately holds.
        double.type(commented.replace("id: my_flow # keep", "id: pasted # theirs"))

        expect(double.current()).toContain("id: my_flow # theirs")
    })

    it("does not provoke a correction for an edit elsewhere in a commented document", () => {
        const commented = FLOW.replace("id: my_flow", "id: my_flow # keep")
        const double = editorDouble(commented)
        guard(double)

        const edited = commented.replace("message: hello", "message: changed")
        double.type(edited)

        // Nothing was violated, so nothing was rewritten.
        expect(double.current()).toBe(edited)
    })

    it("stays out of the way while disabled, as when creating", () => {
        const double = editorDouble(FLOW)
        guard(double, false)

        const renamed = FLOW.replace("id: my_flow", "id: brand_new")
        double.type(renamed)

        expect(double.current()).toBe(renamed)
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

    it("leaves undo working after a keystroke is refused", () => {
        const double = editorDouble(FLOW)
        guard(double)

        // A legitimate edit the user will expect to be able to undo later.
        const edited = FLOW.replace("message: hello", "message: changed")
        double.type(edited)
        expect(double.current()).toBe(edited)

        // A keystroke on a locked line, correctly refused.
        double.type(edited.replace("id: my_flow", "id: my_flowX"))
        expect(double.current()).toBe(edited)

        // The refused keystroke and its correction are one step, so this undoes
        // both and lands on a document that violates nothing.
        double.undo()
        expect(double.current()).toBe(edited)

        // And the legitimate edit is still reachable, which is what a correction
        // in its own undo element takes away.
        double.undo()
        expect(double.current()).toBe(FLOW)
    })

    it("does not grow the undo stack for a correction", () => {
        const double = editorDouble(FLOW)
        guard(double)

        const before = double.undoDepth()
        double.type(FLOW.replace("id: my_flow", "id: my_flowX"))

        // One element for the user's keystroke; the correction joins it rather
        // than adding a second the user would have to press through.
        expect(double.undoDepth()).toBe(before + 1)
    })

    it("does not re-attach when the expectations are replaced by an equal object", async () => {
        const double = editorDouble(FLOW)
        const expected = ref<Record<string, string | undefined>>({
            id: "my_flow",
            namespace: "company.team",
        })
        guardWith(double, expected)

        const attached = double.attachCount()

        // What the flow store does on every save, revision load and refetch: a
        // fresh object carrying the same values. Keying the watcher on the
        // container rather than the values re-ran attach() for each one, which
        // clears the decorations and can strand `lastValid` at undefined.
        expected.value = {id: "my_flow", namespace: "company.team"}
        await new Promise((resolve) => setTimeout(resolve))

        expect(double.attachCount()).toBe(attached)
    })

    it("does re-attach when a locked value actually changes", async () => {
        const double = editorDouble(FLOW)
        const expected = ref<Record<string, string | undefined>>({
            id: "my_flow",
            namespace: "company.team",
        })
        guardWith(double, expected)

        const attached = double.attachCount()

        expected.value = {id: "renamed_flow", namespace: "company.team"}
        await new Promise((resolve) => setTimeout(resolve))

        expect(double.attachCount()).toBe(attached + 1)
    })
})
