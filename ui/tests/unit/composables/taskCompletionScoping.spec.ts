import {describe, expect, it} from "vitest"
import {
    taskTypeAtCursor,
    scopePropertySuggestionsToTaskType,
} from "../../../src/composables/monaco/languages/taskCompletionScoping"

const FLOW = `id: myflow
namespace: my.ns
tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: hi
`

// Monaco's CompletionItemKind.Property; the helper only compares equality, so the exact value is irrelevant.
const PROPERTY = 9
const VALUE = 12

describe("taskTypeAtCursor", () => {
    it("resolves the enclosing task's type when the cursor is inside the task body", () => {
        const cursorIndex = FLOW.indexOf("message: hi") + "message: hi".length
        expect(taskTypeAtCursor({source: FLOW, cursorIndex})).toBe(
            "io.kestra.plugin.core.log.Log",
        )
    })

    it("returns undefined when the cursor is not inside a task", () => {
        const cursorIndex = FLOW.indexOf("namespace") + 3
        expect(taskTypeAtCursor({source: FLOW, cursorIndex})).toBeUndefined()
    })
})

describe("scopePropertySuggestionsToTaskType", () => {
    const suggestions = [
        {label: "message", kind: PROPERTY},
        {label: "commands", kind: PROPERTY},
        {label: "io.kestra.plugin.core.log.Log", kind: VALUE},
    ]

    it("drops property suggestions that do not belong to the resolved type", () => {
        const result = scopePropertySuggestionsToTaskType({
            suggestions,
            validPropertyKeys: ["id", "type", "message"],
            propertyKind: PROPERTY,
        })
        expect(result.map((s) => s.label)).toEqual([
            "message",
            "io.kestra.plugin.core.log.Log",
        ])
    })

    it("leaves non-property suggestions untouched even when their label is not a valid key", () => {
        const result = scopePropertySuggestionsToTaskType({
            suggestions: [{label: "io.kestra.plugin.core.log.Log", kind: VALUE}],
            validPropertyKeys: ["id", "type", "message"],
            propertyKind: PROPERTY,
        })
        expect(result).toHaveLength(1)
    })

    it("fails open (returns every suggestion) when the valid keys are unknown", () => {
        expect(
            scopePropertySuggestionsToTaskType({
                suggestions,
                validPropertyKeys: undefined,
                propertyKind: PROPERTY,
            }),
        ).toHaveLength(suggestions.length)
        expect(
            scopePropertySuggestionsToTaskType({
                suggestions,
                validPropertyKeys: [],
                propertyKind: PROPERTY,
            }),
        ).toHaveLength(suggestions.length)
    })
})
