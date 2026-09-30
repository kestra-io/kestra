import {describe, it, expect} from "vitest"
import {violationMarkers} from "../../../src/utils/violationMarkers"

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

function markerAt(path: string) {
    return violationMarkers(flow, [{pointer: path, detail: "boom"}])[0]
}

describe("violationMarkers", () => {
    it("marks an unknown key through its scalar value", () => {
        expect(markerAt("/tasks/0/tasks/0/unknownProp")).toMatchObject({
            startLineNumber: 9, startColumn: 9, endLineNumber: 9, endColumn: 26,
        })
    })

    it("marks each line of the enclosing block, naming the missing key, leaving indentation, dashes and comments alone", () => {
        expect(violationMarkers(flow, [{pointer: "/tasks/0/tasks/0/message", detail: "boom"}])).toEqual([
            {message: "message: boom", startLineNumber: 7, startColumn: 9, endLineNumber: 7, endColumn: 16},
            {message: "message: boom", startLineNumber: 8, startColumn: 9, endLineNumber: 8, endColumn: 44},
            {message: "message: boom", startLineNumber: 9, startColumn: 9, endLineNumber: 9, endColumn: 26},
        ])
    })

    it("marks only the key when it holds a block", () => {
        expect(markerAt("/tasks/0/tasks")).toMatchObject({
            startLineNumber: 6, startColumn: 5, endLineNumber: 6, endColumn: 10,
        })
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

        expect(violationMarkers(source, [{pointer: "/tasks/0/condition", detail: "must not be null"}])
            .map(marker => marker.startLineNumber)).toEqual([2, 3, 4])
    })

    it("resolves the Java name of a renamed key, such as _finally, to its YAML key", () => {
        const source = "finally:\n  - id: cleanup\n    type: io.kestra.plugin.core.log.Log\n"

        expect(violationMarkers(source, [{pointer: "/_finally/0/message", detail: "must not be null"}])).toEqual([
            {message: "message: must not be null", startLineNumber: 2, startColumn: 5, endLineNumber: 2, endColumn: 16},
            {message: "message: must not be null", startLineNumber: 3, startColumn: 5, endLineNumber: 3, endColumn: 40},
        ])
    })

    it("leaves a violation on the document root to the error panel", () => {
        expect(violationMarkers(flow, [{pointer: "", detail: "boom"}, {pointer: "/labels", detail: "boom"}, {detail: "unlocated"}])).toEqual([])
    })

    it("places nothing on a source that no longer parses", () => {
        expect(violationMarkers("tasks: [", [{pointer: "/tasks/0/id", detail: "boom"}])).toEqual([])
    })
})
