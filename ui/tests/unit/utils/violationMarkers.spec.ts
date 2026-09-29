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
    return violationMarkers(flow, [{path, message: "boom"}])[0]
}

describe("violationMarkers", () => {
    it("marks an unknown key through its scalar value", () => {
        expect(markerAt("/tasks/0/tasks/0/unknownProp")).toMatchObject({
            startLineNumber: 9, startColumn: 9, endLineNumber: 9, endColumn: 26,
        })
    })

    it("marks each line of the enclosing block, leaving indentation, dashes and comments alone", () => {
        expect(violationMarkers(flow, [{path: "/tasks/0/tasks/0/message", message: "boom"}])).toEqual([
            {message: "boom", startLineNumber: 7, startColumn: 9, endLineNumber: 7, endColumn: 16},
            {message: "boom", startLineNumber: 8, startColumn: 9, endLineNumber: 8, endColumn: 44},
            {message: "boom", startLineNumber: 9, startColumn: 9, endLineNumber: 9, endColumn: 26},
        ])
    })

    it("marks only the key when it holds a block", () => {
        expect(markerAt("/tasks/0/tasks")).toMatchObject({
            startLineNumber: 6, startColumn: 5, endLineNumber: 6, endColumn: 10,
        })
    })

    it("leaves a violation on the document root to the error panel", () => {
        expect(violationMarkers(flow, [{path: "", message: "boom"}, {path: "/labels", message: "boom"}])).toEqual([])
    })

    it("places nothing on a source that no longer parses", () => {
        expect(violationMarkers("tasks: [", [{path: "/tasks/0/id", message: "boom"}])).toEqual([])
    })
})
