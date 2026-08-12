import {describe, expect, it} from "vitest"
import {
    findReadOnlyLines,
    readTopLevelValue,
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
