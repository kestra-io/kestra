import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {computed, defineComponent, ref} from "vue"
import {
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
 */
function editorDouble(initial: string) {
    let lines = initial.split("\n")
    const listeners: Array<() => void> = []
    let painted: unknown[] = []

    const model = {
        getValue: () => lines.join("\n"),
        getFullModelRange: () => ({
            startLineNumber: 1,
            startColumn: 1,
            endLineNumber: lines.length,
            endColumn: (lines[lines.length - 1]?.length ?? 0) + 1,
        }),
        getLineMaxColumn: (line: number) => (lines[line - 1]?.length ?? 0) + 1,
    }

    const editor = {
        getModel: () => model,
        onDidChangeModelContent(callback: () => void) {
            listeners.push(callback)
            return {dispose: () => listeners.splice(listeners.indexOf(callback), 1)}
        },
        createDecorationsCollection: () => ({
            set: (next: unknown[]) => { painted = next },
            clear: () => { painted = [] },
        }),
        executeEdits(_source: string, edits: {range: {startLineNumber: number; endLineNumber: number}; text: string}[]) {
            // Descending, so earlier line numbers stay valid as we splice.
            for (const edit of [...edits].sort((a, b) => b.range.startLineNumber - a.range.startLineNumber)) {
                const start = edit.range.startLineNumber - 1
                const count = edit.range.endLineNumber - edit.range.startLineNumber + 1
                lines.splice(start, count, ...edit.text.split("\n"))
            }
            // Monaco notifies synchronously, which is also what exercises the
            // composable's re-entrancy guard.
            listeners.forEach((listener) => listener())
            return true
        },
        getSelection: () => null,
        setSelection: () => {},
    }

    return {
        editor,
        current: () => lines.join("\n"),
        decorationCount: () => painted.length,
        /** Simulate the user changing the buffer. */
        type(next: string) {
            lines = next.split("\n")
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
    function guard(double: ReturnType<typeof editorDouble>, enabled = true) {
        return withComposable(() => useReadOnlyYamlKeys({
            editor: ref(double.editor) as any,
            expected: computed(() => ({id: "my_flow", namespace: "company.team"})),
            enabled: computed(() => enabled),
        }))
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

    it("does not freeze a document whose locked line carries a comment", () => {
        const commented = FLOW.replace("id: my_flow", "id: my_flow # keep")
        const double = editorDouble(commented)
        guard(double)

        const edited = commented.replace("message: hello", "message: changed")
        double.type(edited)

        // The comment survives and the unrelated edit lands.
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
})
