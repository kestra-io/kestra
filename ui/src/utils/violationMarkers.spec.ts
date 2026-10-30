import {describe, it, expect} from "vitest"
import {violationMarkers} from "./violationMarkers"

const flow = `id: hello
namespace: company.team
tasks:
  - id: seq
    type: io.kestra.plugin.core.flow.Sequential
    tasks:
      - id: log
        type: io.kestra.plugin.core.log.Log   # a comment
        unknownProp: nope
`

function linesAt(source: string, pointer: string) {
    return violationMarkers(source, [{pointer, detail: "boom"}]).flatMap(marker => marker.lines)
}

describe("violationMarkers", () => {
    it("marks an unknown key through its scalar value", () => {
        expect(linesAt(flow, "/tasks/0/tasks/0/unknownProp")).toEqual([
            {startLineNumber: 9, startColumn: 9, endLineNumber: 9, endColumn: 26},
        ])
    })

    it("marks each line of the enclosing block once, naming the missing key, leaving indentation, dashes and comments alone", () => {
        expect(violationMarkers(flow, [{pointer: "/tasks/0/tasks/0/message", detail: "boom"}])).toEqual([{
            message: "message: boom",
            lines: [
                {startLineNumber: 7, startColumn: 9, endLineNumber: 7, endColumn: 16},
                {startLineNumber: 8, startColumn: 9, endLineNumber: 8, endColumn: 44},
                {startLineNumber: 9, startColumn: 9, endLineNumber: 9, endColumn: 26},
            ],
        }])
    })

    it("marks only the key when it holds a block", () => {
        expect(linesAt(flow, "/tasks/0/tasks")).toEqual([
            {startLineNumber: 6, startColumn: 5, endLineNumber: 6, endColumn: 10},
        ])
    })

    it("leaves the nested tasks of a block missing a key unmarked", () => {
        const source = [
            "tasks:",
            "  - id: branch",
            "    type: io.kestra.plugin.core.flow.If",
            "    then:",
            "      - id: yes_task",
            "        type: io.kestra.plugin.core.log.Log",
            "        message: yes",
            "",
        ].join("\n")

        expect(linesAt(source, "/tasks/0/condition").map(line => line.startLineNumber)).toEqual([2, 3, 4])
    })

    it("resolves the Java name of a renamed key, such as _finally, to its YAML key", () => {
        const source = "finally:\n  - id: cleanup\n    type: io.kestra.plugin.core.log.Log\n"

        expect(violationMarkers(source, [{pointer: "/_finally/0/message", detail: "must not be null"}])).toEqual([{
            message: "message: must not be null",
            lines: [
                {startLineNumber: 2, startColumn: 5, endLineNumber: 2, endColumn: 16},
                {startLineNumber: 3, startColumn: 5, endLineNumber: 3, endColumn: 40},
            ],
        }])
    })

    it("marks a block scalar on its first line only, leaving its body alone", () => {
        const source = [
            "tasks:",
            "  - id: transform",
            "    type: io.kestra.plugin.scripts.python.Script",
            "    script: |",
            "      import pandas as pd",
            "      print(\"done\")",
            "    beforeCommands:",
            "      - pip install pandas",
            "",
        ].join("\n")

        expect(linesAt(source, "/tasks/0/containerImage").map(line => line.startLineNumber)).toEqual([2, 3, 4, 7])
        expect(linesAt(source, "/tasks/0/script")).toEqual([
            {startLineNumber: 4, startColumn: 5, endLineNumber: 4, endColumn: 14},
        ])
    })

    it("falls back to the last key on the path when the pointer names no scalar", () => {
        expect(linesAt(flow, "/tasks/5/id")).toEqual([
            {startLineNumber: 3, startColumn: 1, endLineNumber: 3, endColumn: 6},
        ])
    })

    it("still marks a source that only has a duplicate key", () => {
        expect(linesAt("id: a\nid: b\nnamespace: c\nunknown: d\n", "/unknown")).toHaveLength(1)
    })

    it("marks the same violation once when it is reported twice", () => {
        const twice = {pointer: "/tasks/0/tasks/0/message", detail: "boom"}
        expect(violationMarkers(flow, [twice, {...twice}])).toHaveLength(1)
    })

    it("leaves a violation on the document root to the error panel", () => {
        expect(violationMarkers(flow, [{pointer: "", detail: "boom"}, {pointer: "/labels", detail: "boom"}, {detail: "unlocated"}])).toEqual([])
    })

    it("places nothing on a source that no longer parses", () => {
        expect(violationMarkers("tasks: [", [{pointer: "/tasks/0/id", detail: "boom"}])).toEqual([])
    })
})
