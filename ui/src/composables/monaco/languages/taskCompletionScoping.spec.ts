import {describe, expect, it} from "vitest"
import {
    taskIdentityAtCursor,
    taskTypeAtCursor,
    findTaskLikeAtCursor,
    scopePropertySuggestionsToTaskType,
    filterMissingRequiredTaskProperties,
} from "./taskCompletionScoping"

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

    it.each(["r", "retr", "timeo", "r:"])(
        "still resolves the type while the property key %j is half typed",
        (partialKey) => {
            const source = `${FLOW}    ${partialKey}`
            expect(taskTypeAtCursor({source, cursorIndex: source.length})).toBe(
                "io.kestra.plugin.core.log.Log",
            )
        },
    )

    it("resolves the innermost task, not its parent, while a key is half typed inside a nested task", () => {
        const nested = `id: myflow
namespace: my.ns
tasks:
  - id: parent
    type: io.kestra.plugin.core.flow.Sequential
    tasks:
      - id: child
        type: io.kestra.plugin.core.log.Log
        message: hi
        retr`
        expect(taskTypeAtCursor({source: nested, cursorIndex: nested.length})).toBe(
            "io.kestra.plugin.core.log.Log",
        )
    })

    it.each([
        ["      maxAttempts: 3", "after a set key"],
        ["      inter", "while a key is half typed"],
    ])("returns undefined inside a nested sub-map (%s, %s)", (tail) => {
        const withRetry = `id: myflow
namespace: my.ns
tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: hi
    retry:
      type: constant
${tail}`
        expect(taskTypeAtCursor({source: withRetry, cursorIndex: withRetry.length})).toBeUndefined()
    })

    it("reports the task's pinned version alongside its type", () => {
        const pinned = `id: myflow
namespace: my.ns
tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    version: 1.2.3
    message: hi
`
        const cursorIndex = pinned.indexOf("message: hi") + "message: hi".length
        expect(taskIdentityAtCursor({source: pinned, cursorIndex})).toEqual({
            type: "io.kestra.plugin.core.log.Log",
            version: "1.2.3",
        })
    })

    it("resolves the task even if its id matches a flow input", () => {
        const flow = `id: myflow
namespace: my.ns
inputs:
  - id: same
    type: STRING
tasks:
  - id: same
    type: io.kestra.plugin.core.log.Log
    mes`
        expect(taskTypeAtCursor({source: flow, cursorIndex: flow.length})).toBe("io.kestra.plugin.core.log.Log")
    })

    it("returns undefined for flow inputs/outputs because they are isolated via YAML path", () => {
        const inputFlow = `id: myflow
namespace: my.ns
inputs:
  - id: myinput
    type: STRING
    defa`
        expect(taskTypeAtCursor({source: inputFlow, cursorIndex: inputFlow.length})).toBeUndefined()

        const outputFlow = `id: myflow
namespace: my.ns
outputs:
  - id: myoutput
    type: STRING
    val`
        expect(taskTypeAtCursor({source: outputFlow, cursorIndex: outputFlow.length})).toBeUndefined()
    })

    it("returns undefined for flow triggers because they are isolated via YAML path", () => {
        const triggerFlow = `id: myflow
namespace: my.ns
triggers:
  - id: mytrigger
    type: io.kestra.plugin.aws.s3.Trigger
    cro`
        expect(taskTypeAtCursor({source: triggerFlow, cursorIndex: triggerFlow.length})).toBeUndefined()
    })

    it("returns undefined for nested maps inside a trigger (e.g. conditions:)", () => {
        const nestedTrigger = `id: myflow
namespace: my.ns
triggers:
  - id: mytrigger
    type: io.kestra.plugin.aws.s3.Trigger
    conditions:
      - id: cond
        type: io.kestra.plugin.core.condition.ExecutionStatus
        in`
        expect(taskTypeAtCursor({source: nestedTrigger, cursorIndex: nestedTrigger.length})).toBeUndefined()
    })

    it("resolves tasks nested inside DAGs with tasks: - task: shape", () => {
        const dagFlow = `id: myflow
namespace: my.ns
tasks:
  - id: dag
    type: io.kestra.plugin.core.dag.Dag
    tasks:
      - task:
          id: inner
          type: io.kestra.plugin.core.log.Log
          mess`
        expect(taskTypeAtCursor({source: dagFlow, cursorIndex: dagFlow.length})).toBe("io.kestra.plugin.core.log.Log")
    })

    it("resolves tasks inside errors: and finally: blocks", () => {
        const errorFlow = `id: myflow
namespace: my.ns
errors:
  - id: err
    type: io.kestra.plugin.core.log.Log
    mess`
        expect(taskTypeAtCursor({source: errorFlow, cursorIndex: errorFlow.length})).toBe("io.kestra.plugin.core.log.Log")

        const finallyFlow = `id: myflow
namespace: my.ns
finally:
  - id: fin
    type: io.kestra.plugin.core.log.Log
    mess`
        expect(taskTypeAtCursor({source: finallyFlow, cursorIndex: finallyFlow.length})).toBe("io.kestra.plugin.core.log.Log")
    })

    it("returns undefined on unparsable documents", () => {
        const badFlow = "id: myflow\n::::unparsable!!!!"
        expect(taskTypeAtCursor({source: badFlow, cursorIndex: badFlow.length})).toBeUndefined()
    })
})

describe("filterMissingRequiredTaskProperties", () => {
    it("works for trigger bodies since they are still identified as task-like", () => {
        const triggerFlow = `id: myflow
namespace: my.ns
triggers:
  - id: mytrigger
    type: io.kestra.plugin.aws.s3.Trigger
    cro`
        expect(
            filterMissingRequiredTaskProperties({
                source: triggerFlow,
                cursorIndex: triggerFlow.length,
                requiredProperties: ["id", "type", "bucket"],
            }),
        ).toEqual(["bucket"])
    })

    it("works for input bodies since they are still identified as task-like", () => {
        const inputFlow = `id: myflow
namespace: my.ns
inputs:
  - id: myinput
    type: STRING
    defa`
        expect(
            filterMissingRequiredTaskProperties({
                source: inputFlow,
                cursorIndex: inputFlow.length,
                requiredProperties: ["id", "type", "required"],
            }),
        ).toEqual(["required"])
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

describe("findTaskLikeAtCursor", () => {
    it("resolves the enclosing task when cursor is inside a top-level task", () => {
        const cursorIndex = FLOW.indexOf("message: hi") + "message: hi".length
        const task = findTaskLikeAtCursor({source: FLOW, cursorIndex})

        expect(task).toBeDefined()
        expect(task?.id).toBe("hello")
        expect(task?.type).toBe("io.kestra.plugin.core.log.Log")
        expect(task?.message).toBe("hi")
    })

    it("resolves the inner task when cursor is inside a nested flowable's task", () => {
        const nested = `id: myflow
namespace: my.ns
tasks:
  - id: parent
    type: io.kestra.plugin.core.flow.Sequential
    tasks:
      - id: child
        type: io.kestra.plugin.core.log.Log
        message: inner
`
        const cursorIndex = nested.indexOf("message: inner") + "message: inner".length
        const task = findTaskLikeAtCursor({source: nested, cursorIndex})

        expect(task).toBeDefined()
        expect(task?.id).toBe("child")
        expect(task?.type).toBe("io.kestra.plugin.core.log.Log")
    })

    it("resolves to neither when cursor is outside of any task", () => {
        const flowWithHeader = `id: myflow
namespace: my.ns

tasks:
  - id: first
    type: io.kestra.plugin.core.log.Log
    message: first
`
        // Cursor at namespace line
        const outsideIndex = flowWithHeader.indexOf("namespace") + 3
        expect(findTaskLikeAtCursor({source: flowWithHeader, cursorIndex: outsideIndex})).toBeUndefined()
    })

    it("resolves to the task when cursor is inside errors block", () => {
        const errorFlow = `id: myflow
namespace: my.ns
errors:
  - id: error_handler
    type: io.kestra.plugin.core.log.Log
    message: failed
`
        const cursorIndex = errorFlow.indexOf("message: failed") + "message: failed".length
        const task = findTaskLikeAtCursor({source: errorFlow, cursorIndex})

        expect(task).toBeDefined()
        expect(task?.id).toBe("error_handler")
        expect(task?.type).toBe("io.kestra.plugin.core.log.Log")
    })

    it("returns undefined without throwing when YAML is malformed", () => {
        const malformed = `id: myflow
tasks:
  - id: [invalid yaml syntax {{{{ ::::
`
        expect(() => findTaskLikeAtCursor({source: malformed, cursorIndex: malformed.length})).not.toThrow()
        expect(findTaskLikeAtCursor({source: malformed, cursorIndex: malformed.length})).toBeUndefined()
    })

    it("returns undefined when source is empty", () => {
        expect(findTaskLikeAtCursor({source: "", cursorIndex: 0})).toBeUndefined()
    })
})

