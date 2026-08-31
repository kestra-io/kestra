import {describe, it, expect} from "vitest"
import * as flowYamlUtils from "@kestra-io/topology/flow-yaml-utils"
import {
    addBlock,
    addBlockAtPath,
    buildMinimalTask,
    collectAllIds,
    deleteBlock,
    deleteBlockAtPath,
    displayTaskOf,
    duplicateBlock,
    duplicateBlockAtPath,
    groupValidationIssuesByTask,
    isFlowableType,
    isWrappedLaneItem,
    isWrapperLane,
    moveBlockAtPath,
    reorderAtPath,
    resolveBlockDomId,
    taskEditPathFor,
    updateBlock,
    updateBlockAtPath,
    wrapAsDagTask,
} from "../../../src/utils/flowableBlockOps"

const SIMPLE_FLOW = `
id: my_flow
namespace: company.team
tasks:
  - id: task_a
    type: io.kestra.plugin.core.log.Log
    message: Hello
  - id: task_b
    type: io.kestra.plugin.core.log.Log
    message: World
`.trim()

const FLOW_WITH_FLOWABLE = `
id: my_flow
namespace: company.team
tasks:
  - id: leaf_task
    type: io.kestra.plugin.core.log.Log
    message: Before
  - id: if_task
    type: io.kestra.plugin.core.flow.If
    condition: "{{ true }}"
    then:
      - id: nested_a
        type: io.kestra.plugin.core.log.Log
        message: In then
    else:
      - id: nested_b
        type: io.kestra.plugin.core.log.Log
        message: In else
`.trim()

const FLOW_WITH_TRIGGERS = `
id: my_flow
namespace: company.team
tasks:
  - id: log
    type: io.kestra.plugin.core.log.Log
    message: ok
triggers:
  - id: webhook
    type: io.kestra.plugin.core.trigger.Webhook
    key: abc
`.trim()

const FLOW_WITH_SWITCH = `
id: my_flow
namespace: company.team
tasks:
  - id: sw
    type: io.kestra.plugin.core.flow.Switch
    value: "{{ inputs.env }}"
    cases:
      prod:
        - id: prod_log
          type: io.kestra.plugin.core.log.Log
          message: Production
      dev:
        - id: dev_log
          type: io.kestra.plugin.core.log.Log
          message: Development
    defaults:
      - id: default_log
        type: io.kestra.plugin.core.log.Log
        message: Default
`.trim()

const FLOW_WITH_PARALLEL = `
id: my_flow
namespace: company.team
tasks:
  - id: parallel_task
    type: io.kestra.plugin.core.flow.Parallel
    tasks:
      - id: sub_a
        type: io.kestra.plugin.core.log.Log
        message: A
      - id: sub_b
        type: io.kestra.plugin.core.log.Log
        message: B
`.trim()

const FLOW_WITH_DAG = `
id: my_flow
namespace: company.team
tasks:
  - id: my_dag
    type: io.kestra.plugin.core.flow.Dag
    tasks:
      - task:
          id: a
          type: io.kestra.plugin.core.log.Log
          message: A
      - task:
          id: b
          type: io.kestra.plugin.core.log.Log
          message: B
        dependsOn:
          - a
`.trim()

describe("flowableBlockOps", () => {
    describe("addBlock", () => {
        it("appends a task to the end of the tasks section", () => {
            // Given
            const newTask = {id: "task_c", type: "io.kestra.plugin.core.log.Log", message: "New"}

            // When
            const result = addBlock(SIMPLE_FLOW, "tasks", newTask)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks).toHaveLength(3)
            expect(parsed.tasks[2].id).toBe("task_c")
        })

        it("appends after a specific task when afterId is provided", () => {
            // Given
            const newTask = {id: "task_between", type: "io.kestra.plugin.core.log.Log"}

            // When
            const result = addBlock(SIMPLE_FLOW, "tasks", newTask, "task_a")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks).toHaveLength(3)
            expect(parsed.tasks[1].id).toBe("task_between")
            expect(parsed.tasks[2].id).toBe("task_b")
        })

        it("creates the tasks section if it does not exist", () => {
            // Given
            const emptyFlow = "id: my_flow\nnamespace: company.team"
            const newTask = {id: "first", type: "io.kestra.plugin.core.log.Log"}

            // When
            const result = addBlock(emptyFlow, "tasks", newTask)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks).toHaveLength(1)
            expect(parsed.tasks[0].id).toBe("first")
        })
    })

    describe("addBlockAtPath", () => {
        it("adds a task into If.then branch", () => {
            // Given
            const newTask = {id: "then_new", type: "io.kestra.plugin.core.log.Log", message: "Added to then"}

            // When
            const result = addBlockAtPath(FLOW_WITH_FLOWABLE, "tasks[1].then", newTask)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[1].then).toHaveLength(2)
            expect(parsed.tasks[1].then[1].id).toBe("then_new")
        })

        it("adds a task into If.else branch", () => {
            // Given
            const newTask = {id: "else_new", type: "io.kestra.plugin.core.log.Log"}

            // When
            const result = addBlockAtPath(FLOW_WITH_FLOWABLE, "tasks[1].else", newTask)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[1].else).toHaveLength(2)
            expect(parsed.tasks[1].else[1].id).toBe("else_new")
        })

        it("auto-creates the else branch when adding the first task to an empty else", () => {
            // Given — no else branch on the If
            const flowNoElse = `
id: my_flow
namespace: company.team
tasks:
  - id: if_task
    type: io.kestra.plugin.core.flow.If
    condition: "{{ true }}"
    then:
      - id: then_a
        type: io.kestra.plugin.core.log.Log
`.trim()
            const newTask = {id: "else_first", type: "io.kestra.plugin.core.log.Log"}

            // When
            const result = addBlockAtPath(flowNoElse, "tasks[0].else", newTask)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].else).toHaveLength(1)
            expect(parsed.tasks[0].else[0].id).toBe("else_first")
        })

        it("adds a task into a Switch case lane", () => {
            // Given
            const newTask = {id: "prod_second", type: "io.kestra.plugin.core.log.Log"}

            // When
            const result = addBlockAtPath(FLOW_WITH_SWITCH, "tasks[0].cases.prod", newTask)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].cases.prod).toHaveLength(2)
            expect(parsed.tasks[0].cases.prod[1].id).toBe("prod_second")
        })

        it("auto-creates a new Switch case lane when adding the first task to cases.X", () => {
            // Given — staging case does not exist yet
            const newTask = {id: "staging_log", type: "io.kestra.plugin.core.log.Log"}

            // When
            const result = addBlockAtPath(FLOW_WITH_SWITCH, "tasks[0].cases.staging", newTask)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].cases.staging).toHaveLength(1)
            expect(parsed.tasks[0].cases.staging[0].id).toBe("staging_log")
            expect(parsed.tasks[0].cases.prod).toHaveLength(1)
            expect(parsed.tasks[0].cases.dev).toHaveLength(1)
        })

        it("adds a task into Parallel.tasks branch", () => {
            // Given
            const newTask = {id: "sub_c", type: "io.kestra.plugin.core.log.Log", message: "C"}

            // When
            const result = addBlockAtPath(FLOW_WITH_PARALLEL, "tasks[0].tasks", newTask)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].tasks).toHaveLength(3)
            expect(parsed.tasks[0].tasks[2].id).toBe("sub_c")
        })

        it("adds a task into flow-level errors lane", () => {
            // Given
            const flowWithErrors = `
id: my_flow
namespace: company.team
tasks:
  - id: log
    type: io.kestra.plugin.core.log.Log
errors:
  - id: err_log
    type: io.kestra.plugin.core.log.Log
`.trim()
            const newTask = {id: "err_notify", type: "io.kestra.plugin.core.log.Log"}

            // When
            const result = addBlockAtPath(flowWithErrors, "errors", newTask)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.errors).toHaveLength(2)
            expect(parsed.errors[1].id).toBe("err_notify")
        })
    })

    describe("deleteBlock", () => {
        it("removes a task by id", () => {
            // Given

            // When
            const result = deleteBlock(SIMPLE_FLOW, "tasks", "task_a")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks).toHaveLength(1)
            expect(parsed.tasks[0].id).toBe("task_b")
        })

        it("removes a trigger by id", () => {
            // Given

            // When
            const result = deleteBlock(FLOW_WITH_TRIGGERS, "triggers", "webhook")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.triggers).toBeUndefined()
        })

        it("returns source unchanged when id is not found", () => {
            // Given

            // When
            const result = deleteBlock(SIMPLE_FLOW, "tasks", "nonexistent")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks).toHaveLength(2)
        })
    })

    describe("deleteBlockAtPath", () => {
        it("removes a nested task by full path and cleans up the empty array key", () => {
            // Given

            // When
            const result = deleteBlockAtPath(FLOW_WITH_FLOWABLE, "tasks[1].then[0]")

            // Then — the item is removed and the empty then key is stripped
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[1].then).toBeUndefined()
        })

        it("removes a task from a Switch case lane and cleans up empty case array", () => {
            // Given

            // When
            const result = deleteBlockAtPath(FLOW_WITH_SWITCH, "tasks[0].cases.prod[0]")

            // Then — prod case was the only item, its array is cleaned; dev is untouched
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].cases.prod).toBeUndefined()
            expect(parsed.tasks[0].cases.dev).toHaveLength(1)
        })

        it("preserves sibling branches when deleting from one, cleans up the emptied branch", () => {
            // Given

            // When
            const result = deleteBlockAtPath(FLOW_WITH_FLOWABLE, "tasks[1].then[0]")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[1].then).toBeUndefined()
            expect(parsed.tasks[1].else).toHaveLength(1)
            expect(parsed.tasks[1].else[0].id).toBe("nested_b")
        })

        it("keeps unrelated comments when deleting a nested task", () => {
            // Given — a comment on a task unrelated to the flowable being edited
            const flowWithComment = `
id: my_flow
namespace: company.team
tasks:
  # keep me: an unrelated reminder
  - id: leaf_task
    type: io.kestra.plugin.core.log.Log
    message: Before
  - id: if_task
    type: io.kestra.plugin.core.flow.If
    condition: "{{ true }}"
    then:
      - id: nested_a
        type: io.kestra.plugin.core.log.Log
        message: In then
    else:
      - id: nested_b
        type: io.kestra.plugin.core.log.Log
        message: In else
`.trim()

            // When — delete a task nested inside the If (routes through pruneEmptySequences)
            const result = deleteBlockAtPath(flowWithComment, "tasks[1].then[0]")

            // Then — the empty branch is cleaned AND the unrelated comment survives
            expect(result).toContain("# keep me: an unrelated reminder")
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[1].then).toBeUndefined()
            expect(parsed.tasks[0].id).toBe("leaf_task")
        })
    })

    describe("duplicateBlock", () => {
        it("creates a copy of a task with a new id", () => {
            // Given

            // When
            const result = duplicateBlock(SIMPLE_FLOW, "tasks", "task_a")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks).toHaveLength(3)
            const copy = parsed.tasks.find((t: Record<string, unknown>) => String(t.id).startsWith("task_a_copy"))
            expect(copy).toBeDefined()
            expect(copy.type).toBe("io.kestra.plugin.core.log.Log")
        })

        it("appends the copy after the original", () => {
            // Given

            // When
            const result = duplicateBlock(SIMPLE_FLOW, "tasks", "task_a")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].id).toBe("task_a")
            expect(String(parsed.tasks[1].id)).toMatch(/^task_a_copy/)
            expect(parsed.tasks[2].id).toBe("task_b")
        })

        it("avoids id collision by incrementing suffix", () => {
            // Given
            const flowWithCopy = `
id: my_flow
namespace: company.team
tasks:
  - id: task_a
    type: io.kestra.plugin.core.log.Log
  - id: task_a_copy
    type: io.kestra.plugin.core.log.Log
`.trim()

            // When
            const result = duplicateBlock(flowWithCopy, "tasks", "task_a")

            // Then
            const parsed = flowYamlUtils.parse(result)
            const ids = parsed.tasks.map((t: Record<string, unknown>) => t.id)
            expect(ids).toContain("task_a")
            expect(ids).toContain("task_a_copy")
            expect(ids).toContain("task_a_copy_2")
        })

        it("avoids cross-section id collision when duplicating a trigger whose copy id exists in tasks", () => {
            // Given — task already has id "webhook_copy"; trigger id "webhook" will produce "webhook_copy"
            const flowWithCrossCollision = `
id: my_flow
namespace: company.team
tasks:
  - id: webhook_copy
    type: io.kestra.plugin.core.log.Log
    message: same id as the upcoming trigger duplicate
triggers:
  - id: webhook
    type: io.kestra.plugin.core.trigger.Webhook
    key: abc
`.trim()

            // When
            const result = duplicateBlock(flowWithCrossCollision, "triggers", "webhook")

            // Then — "webhook_copy" is taken by the task so the trigger copy must get a different id
            const parsed = flowYamlUtils.parse(result)
            const triggerIds = parsed.triggers.map((t: Record<string, unknown>) => String(t.id))
            expect(triggerIds).toContain("webhook")
            expect(triggerIds).not.toContain("webhook_copy")
            expect(triggerIds.some((id: string) => id.startsWith("webhook_copy_"))).toBe(true)
        })

        it("renames all nested task IDs when duplicating a flowable block", () => {
            // Given — if_task has nested_a in then and nested_b in else
            const originalIds = new Set(["leaf_task", "if_task", "nested_a", "nested_b"])

            // When
            const result = duplicateBlock(FLOW_WITH_FLOWABLE, "tasks", "if_task")

            // Then — the duplicate has unique IDs for itself and all nested tasks
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks).toHaveLength(3)
            const copy = parsed.tasks[2]
            expect(String(copy.id)).toMatch(/^if_task_copy/)

            const copyThenId = String(copy.then[0].id)
            const copyElseId = String(copy.else[0].id)
            expect(originalIds.has(copyThenId)).toBe(false)
            expect(originalIds.has(copyElseId)).toBe(false)
            expect(copyThenId).not.toBe(copyElseId)

            const allIds = new Set([
                ...parsed.tasks.map((t: Record<string, unknown>) => String(t.id)),
                ...parsed.tasks.flatMap((t: Record<string, unknown>) =>
                    Array.isArray(t.then) ? (t.then as Record<string, unknown>[]).map(n => String(n.id)) : [],
                ),
                ...parsed.tasks.flatMap((t: Record<string, unknown>) =>
                    Array.isArray(t.else) ? (t.else as Record<string, unknown>[]).map(n => String(n.id)) : [],
                ),
            ])
            expect(allIds.size).toBe(parsed.tasks.length + copy.then.length + copy.else.length + 2)
        })

        it("renames nested IDs in a Switch block when duplicating, preserving all cases", () => {
            // Given — sw has prod_log, dev_log, default_log nested under cases/defaults
            const originalIds = new Set(["sw", "prod_log", "dev_log", "default_log"])

            // When
            const result = duplicateBlock(FLOW_WITH_SWITCH, "tasks", "sw")

            // Then — copy top-level ID is new
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks).toHaveLength(2)
            const copy = parsed.tasks[1]
            expect(String(copy.id)).toMatch(/^sw_copy/)

            // All nested IDs are renamed and unique vs originals
            const copyProdId = String(copy.cases.prod[0].id)
            const copyDevId = String(copy.cases.dev[0].id)
            const copyDefaultId = String(copy.defaults[0].id)
            expect(originalIds.has(copyProdId)).toBe(false)
            expect(originalIds.has(copyDevId)).toBe(false)
            expect(originalIds.has(copyDefaultId)).toBe(false)
            expect(new Set([copyProdId, copyDevId, copyDefaultId]).size).toBe(3)
        })

        it("returns source unchanged when id is not found", () => {
            // Given

            // When
            const result = duplicateBlock(SIMPLE_FLOW, "tasks", "nonexistent")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks).toHaveLength(2)
        })
    })

    describe("duplicateBlockAtPath", () => {
        it("duplicates a nested task within its lane by full path", () => {
            // Given

            // When
            const result = duplicateBlockAtPath(FLOW_WITH_FLOWABLE, "tasks[1].then[0]")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[1].then).toHaveLength(2)
            expect(String(parsed.tasks[1].then[1].id)).toMatch(/^nested_a_copy/)
        })

        it("avoids id collisions across nested branches when duplicating", () => {
            // Given — nested_a_copy already exists in else
            const flowWithCopy = `
id: my_flow
namespace: company.team
tasks:
  - id: if_task
    type: io.kestra.plugin.core.flow.If
    condition: "{{ true }}"
    then:
      - id: nested_a
        type: io.kestra.plugin.core.log.Log
    else:
      - id: nested_a_copy
        type: io.kestra.plugin.core.log.Log
`.trim()

            // When
            const result = duplicateBlockAtPath(flowWithCopy, "tasks[0].then[0]")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].then).toHaveLength(2)
            const copyId = String(parsed.tasks[0].then[1].id)
            expect(copyId).toMatch(/^nested_a_copy/)
            expect(copyId).not.toBe("nested_a_copy")
        })

        it("renames all nested IDs when duplicating a flowable block via path", () => {
            // Given — outer_if has inner_if in then, inner_if has deep_task
            const deepFlow = `
id: my_flow
namespace: company.team
tasks:
  - id: outer_if
    type: io.kestra.plugin.core.flow.If
    condition: "{{ true }}"
    then:
      - id: inner_if
        type: io.kestra.plugin.core.flow.If
        condition: "{{ false }}"
        then:
          - id: deep_task
            type: io.kestra.plugin.core.log.Log
`.trim()
            const originalIds = new Set(["outer_if", "inner_if", "deep_task"])

            // When — duplicate outer_if
            const result = duplicateBlockAtPath(deepFlow, "tasks[0]")

            // Then — the copy and all its nested IDs are unique vs originals
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks).toHaveLength(2)
            const copy = parsed.tasks[1]
            expect(originalIds.has(String(copy.id))).toBe(false)
            const copyInner = copy.then[0]
            expect(originalIds.has(String(copyInner.id))).toBe(false)
            const copyDeep = copyInner.then[0]
            expect(originalIds.has(String(copyDeep.id))).toBe(false)
            expect(new Set([String(copy.id), String(copyInner.id), String(copyDeep.id)]).size).toBe(3)
        })

        it("duplicates a task within a Switch case lane", () => {
            // Given

            // When
            const result = duplicateBlockAtPath(FLOW_WITH_SWITCH, "tasks[0].cases.prod[0]")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].cases.prod).toHaveLength(2)
            expect(String(parsed.tasks[0].cases.prod[1].id)).toMatch(/^prod_log_copy/)
        })
    })

    describe("updateBlock", () => {
        it("replaces a task's content by id leaving siblings untouched", () => {
            // Given
            const updatedYaml = "id: task_a\ntype: io.kestra.plugin.core.log.Log\nmessage: Updated"

            // When
            const result = updateBlock(SIMPLE_FLOW, "tasks", "task_a", updatedYaml)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks).toHaveLength(2)
            expect(parsed.tasks[0].message).toBe("Updated")
            expect(parsed.tasks[1].id).toBe("task_b")
            expect(parsed.tasks[1].message).toBe("World")
        })

        it("preserves nested branches of sibling flowable tasks when updating a leaf", () => {
            // Given
            const updatedYaml = "id: leaf_task\ntype: io.kestra.plugin.core.log.Log\nmessage: Changed"

            // When
            const result = updateBlock(FLOW_WITH_FLOWABLE, "tasks", "leaf_task", updatedYaml)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].message).toBe("Changed")
            const ifTask = parsed.tasks.find((t: Record<string, unknown>) => t.id === "if_task")
            expect(ifTask.then).toHaveLength(1)
            expect(ifTask.else).toHaveLength(1)
            expect(ifTask.then[0].id).toBe("nested_a")
            expect(ifTask.else[0].id).toBe("nested_b")
        })

        it("updates a trigger without affecting tasks", () => {
            // Given
            const updatedYaml = "id: webhook\ntype: io.kestra.plugin.core.trigger.Webhook\nkey: new-key"

            // When
            const result = updateBlock(FLOW_WITH_TRIGGERS, "triggers", "webhook", updatedYaml)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.triggers[0].key).toBe("new-key")
            expect(parsed.tasks[0].id).toBe("log")
        })

        it("renames the block id when new content has a different id, preserving position and siblings", () => {
            // Given
            const renamedYaml = "id: task_renamed\ntype: io.kestra.plugin.core.log.Log\nmessage: x"

            // When
            const result = updateBlock(SIMPLE_FLOW, "tasks", "task_a", renamedYaml)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks).toHaveLength(2)
            expect(parsed.tasks[0].id).toBe("task_renamed")
            expect(parsed.tasks[1].id).toBe("task_b")
        })

        it("returns source unchanged when id is not found", () => {
            // Given

            // When
            const result = updateBlock(SIMPLE_FLOW, "tasks", "nonexistent", "id: nonexistent\ntype: io.kestra.plugin.core.log.Log")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks).toHaveLength(2)
        })
    })

    describe("updateBlockAtPath", () => {
        it("updates a nested task by full path", () => {
            // Given
            const updatedYaml = "id: nested_a\ntype: io.kestra.plugin.core.log.Log\nmessage: Updated nested"

            // When
            const result = updateBlockAtPath(FLOW_WITH_FLOWABLE, "tasks[1].then[0]", updatedYaml)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[1].then[0].message).toBe("Updated nested")
            expect(parsed.tasks[1].else[0].id).toBe("nested_b")
        })
    })

    describe("Switch.cases map round-trip", () => {
        it("preserves all Switch cases through parse/stringify", () => {
            // Given

            // When
            const parsed1 = flowYamlUtils.parse(FLOW_WITH_SWITCH)
            const stringified = flowYamlUtils.stringify(parsed1)
            const parsed2 = flowYamlUtils.parse(stringified)

            // Then
            expect(parsed2.tasks[0].cases.prod).toHaveLength(1)
            expect(parsed2.tasks[0].cases.prod[0].id).toBe("prod_log")
            expect(parsed2.tasks[0].cases.dev).toHaveLength(1)
            expect(parsed2.tasks[0].cases.dev[0].id).toBe("dev_log")
            expect(parsed2.tasks[0].defaults[0].id).toBe("default_log")
        })

        it("adding a case to Switch preserves all other cases", () => {
            // Given
            const newTask = {id: "staging_log", type: "io.kestra.plugin.core.log.Log"}

            // When
            const result = addBlockAtPath(FLOW_WITH_SWITCH, "tasks[0].cases.staging", newTask)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].cases.prod).toHaveLength(1)
            expect(parsed.tasks[0].cases.dev).toHaveLength(1)
            expect(parsed.tasks[0].cases.staging).toHaveLength(1)
            expect(parsed.tasks[0].cases.staging[0].id).toBe("staging_log")
        })
    })

    describe("Switch case keys containing path delimiters", () => {
        // A Switch case value is an arbitrary string ("1.0", "eu.prod", ...).
        // The blocks editor builds the lane path with the bracket-quoted form
        // (flowYamlUtils.appendKeyToPath), so a dot inside the key is never
        // mistaken for a path separator that would corrupt the document.
        const FLOW_WITH_DOTTED_CASE = `
id: my_flow
namespace: company.team
tasks:
  - id: sw
    type: io.kestra.plugin.core.flow.Switch
    value: "{{ inputs.version }}"
    cases:
      "1.0":
        - id: first
          type: io.kestra.plugin.core.log.Log
          message: one
        - id: second
          type: io.kestra.plugin.core.log.Log
          message: two
`.trim()

        it("adds a case whose key contains a dot without nesting it", () => {
            // Given
            const newTask = {id: "eu_log", type: "io.kestra.plugin.core.log.Log"}

            // When
            const result = addBlockAtPath(FLOW_WITH_SWITCH, "tasks[0].cases[\"eu.prod\"]", newTask)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].cases["eu.prod"]).toHaveLength(1)
            expect(parsed.tasks[0].cases["eu.prod"][0].id).toBe("eu_log")
            // the dot must not have created a nested `eu: { prod: ... }`
            expect(parsed.tasks[0].cases.eu).toBeUndefined()
            expect(parsed.tasks[0].cases.prod).toHaveLength(1)
        })

        it("reorders tasks inside a dotted-key case lane", () => {
            // When
            const result = reorderAtPath(FLOW_WITH_DOTTED_CASE, "tasks[0].cases[\"1.0\"]", 0, 1)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].cases["1.0"].map((t: {id: string}) => t.id)).toEqual(["second", "first"])
        })

        it("moves a task inside a dotted-key case lane", () => {
            // When
            const result = moveBlockAtPath(FLOW_WITH_DOTTED_CASE, "tasks[0].cases[\"1.0\"][1]", "up")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].cases["1.0"].map((t: {id: string}) => t.id)).toEqual(["second", "first"])
        })
    })

    describe("moveBlockAtPath", () => {
        it("moves a task up by one position", () => {
            // Given

            // When
            const result = moveBlockAtPath(SIMPLE_FLOW, "tasks[1]", "up")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks).toHaveLength(2)
            expect(parsed.tasks[0].id).toBe("task_b")
            expect(parsed.tasks[1].id).toBe("task_a")
        })

        it("moves a task down by one position", () => {
            // Given

            // When
            const result = moveBlockAtPath(SIMPLE_FLOW, "tasks[0]", "down")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks).toHaveLength(2)
            expect(parsed.tasks[0].id).toBe("task_b")
            expect(parsed.tasks[1].id).toBe("task_a")
        })

        it("is a no-op when moving the first item up", () => {
            // Given

            // When
            const result = moveBlockAtPath(SIMPLE_FLOW, "tasks[0]", "up")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].id).toBe("task_a")
            expect(parsed.tasks[1].id).toBe("task_b")
        })

        it("is a no-op when moving the last item down", () => {
            // Given

            // When
            const result = moveBlockAtPath(SIMPLE_FLOW, "tasks[1]", "down")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].id).toBe("task_a")
            expect(parsed.tasks[1].id).toBe("task_b")
        })

        it("moves a nested task within a lane", () => {
            // Given — tasks[1].then has nested_a at [0]; there is only one item; add another first
            const withTwo = addBlockAtPath(
                FLOW_WITH_FLOWABLE,
                "tasks[1].then",
                {id: "nested_c", type: "io.kestra.plugin.core.log.Log"},
            )

            // When
            const result = moveBlockAtPath(withTwo, "tasks[1].then[1]", "up")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[1].then[0].id).toBe("nested_c")
            expect(parsed.tasks[1].then[1].id).toBe("nested_a")
        })

        it("preserves the full content of the moved blocks (round-trip safety)", () => {
            // Given

            // When
            const result = moveBlockAtPath(FLOW_WITH_FLOWABLE, "tasks[0]", "down")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].id).toBe("if_task")
            expect(parsed.tasks[0].then[0].id).toBe("nested_a")
            expect(parsed.tasks[0].else[0].id).toBe("nested_b")
            expect(parsed.tasks[1].id).toBe("leaf_task")
        })

        it("returns source unchanged when path has no bracket index", () => {
            // Given

            // When
            const result = moveBlockAtPath(SIMPLE_FLOW, "tasks", "up")

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].id).toBe("task_a")
        })

        it("moves a task up within a Switch.cases lane, preserving the full cases map", () => {
            // Given — prod case has prod_log at [0]; add a second task to make a movable pair
            const withTwo = addBlockAtPath(
                FLOW_WITH_SWITCH,
                "tasks[0].cases.prod",
                {id: "prod_second", type: "io.kestra.plugin.core.log.Log"},
            )

            // When — move prod_second (index 1) up
            const result = moveBlockAtPath(withTwo, "tasks[0].cases.prod[1]", "up")

            // Then — order swapped, other cases intact
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].cases.prod[0].id).toBe("prod_second")
            expect(parsed.tasks[0].cases.prod[1].id).toBe("prod_log")
            expect(parsed.tasks[0].cases.dev).toHaveLength(1)
            expect(parsed.tasks[0].cases.dev[0].id).toBe("dev_log")
            expect(parsed.tasks[0].defaults[0].id).toBe("default_log")
        })

        it("moves a task down within a Switch.cases lane, preserving the full cases map", () => {
            // Given — add a second task to prod so there are two
            const withTwo = addBlockAtPath(
                FLOW_WITH_SWITCH,
                "tasks[0].cases.prod",
                {id: "prod_second", type: "io.kestra.plugin.core.log.Log"},
            )

            // When — move prod_log (index 0) down
            const result = moveBlockAtPath(withTwo, "tasks[0].cases.prod[0]", "down")

            // Then — swapped, siblings untouched
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].cases.prod[0].id).toBe("prod_second")
            expect(parsed.tasks[0].cases.prod[1].id).toBe("prod_log")
            expect(parsed.tasks[0].cases.dev[0].id).toBe("dev_log")
        })
    })

    describe("isFlowableType", () => {
        it("treats a known flow-control suffix as flowable even when the icon flag is false", () => {
            // A lazily-resolved ecosystem icon reports flowable=false; the suffix
            // match must still win so the task renders as a cluster, not a leaf.
            const icons = {"io.kestra.plugin.core.flow.Sequential": {flowable: false}}
            expect(isFlowableType("io.kestra.plugin.core.flow.Sequential", icons)).toBe(true)
        })

        it("falls back to the icon flowable flag for a type without a known suffix", () => {
            const icons = {"io.acme.custom.MyFlowable": {flowable: true}}
            expect(isFlowableType("io.acme.custom.MyFlowable", icons)).toBe(true)
            expect(isFlowableType("io.acme.custom.PlainTask", {"io.acme.custom.PlainTask": {flowable: false}})).toBe(false)
        })

        it("is not flowable for a plain task with no icon entry", () => {
            expect(isFlowableType("io.kestra.plugin.core.log.Log")).toBe(false)
        })
    })

    describe("reorderAtPath", () => {
        it("moves an item from index 0 to index 1 in the top-level tasks list", () => {
            // Given

            // When
            const result = reorderAtPath(SIMPLE_FLOW, "tasks", 0, 1)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].id).toBe("task_b")
            expect(parsed.tasks[1].id).toBe("task_a")
        })

        it("moves an item from index 1 to index 0 (reverse)", () => {
            // Given

            // When
            const result = reorderAtPath(SIMPLE_FLOW, "tasks", 1, 0)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].id).toBe("task_b")
            expect(parsed.tasks[1].id).toBe("task_a")
        })

        it("is a no-op when fromIndex equals toIndex", () => {
            // Given

            // When
            const result = reorderAtPath(SIMPLE_FLOW, "tasks", 0, 0)

            // Then
            expect(result).toBe(SIMPLE_FLOW)
        })

        it("reorders within a nested lane (Parallel.tasks)", () => {
            // Given

            // When
            const result = reorderAtPath(FLOW_WITH_PARALLEL, "tasks[0].tasks", 0, 1)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].tasks[0].id).toBe("sub_b")
            expect(parsed.tasks[0].tasks[1].id).toBe("sub_a")
        })

        it("preserves nested content of moved items (round-trip safety)", () => {
            // Given — if_task contains nested then/else branches
            const threeItems = `
id: my_flow
namespace: company.team
tasks:
  - id: task_a
    type: io.kestra.plugin.core.log.Log
  - id: if_task
    type: io.kestra.plugin.core.flow.If
    condition: "{{ true }}"
    then:
      - id: nested_a
        type: io.kestra.plugin.core.log.Log
  - id: task_c
    type: io.kestra.plugin.core.log.Log
`.trim()

            // When — move if_task from index 1 to index 0
            const result = reorderAtPath(threeItems, "tasks", 1, 0)

            // Then — nested content intact
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].id).toBe("if_task")
            expect(parsed.tasks[0].then[0].id).toBe("nested_a")
            expect(parsed.tasks[1].id).toBe("task_a")
            expect(parsed.tasks[2].id).toBe("task_c")
        })

        it("reorders within a Switch.cases lane, preserving all other cases", () => {
            // Given — add a second task to prod so there are two to reorder
            const withTwo = addBlockAtPath(
                FLOW_WITH_SWITCH,
                "tasks[0].cases.prod",
                {id: "prod_second", type: "io.kestra.plugin.core.log.Log"},
            )

            // When
            const result = reorderAtPath(withTwo, "tasks[0].cases.prod", 0, 1)

            // Then — order swapped, other cases intact
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].cases.prod[0].id).toBe("prod_second")
            expect(parsed.tasks[0].cases.prod[1].id).toBe("prod_log")
            expect(parsed.tasks[0].cases.dev[0].id).toBe("dev_log")
            expect(parsed.tasks[0].defaults[0].id).toBe("default_log")
        })

        it("returns source unchanged when indices are out of bounds", () => {
            // Given

            // When
            const result = reorderAtPath(SIMPLE_FLOW, "tasks", 0, 99)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].id).toBe("task_a")
            expect(parsed.tasks[1].id).toBe("task_b")
        })
    })

    describe("collectAllIds", () => {
        it("collects the ids of every root section, not only the task tree", () => {
            // Given
            const flow = `
id: my_flow
namespace: company.team
triggers:
  - id: schedule
    type: io.kestra.plugin.core.trigger.Schedule
tasks:
  - id: log
    type: io.kestra.plugin.core.log.Log
errors:
  - id: on_error
    type: io.kestra.plugin.core.log.Log
finally:
  - id: cleanup
    type: io.kestra.plugin.core.log.Log
afterExecution:
  - id: notify
    type: io.kestra.plugin.core.log.Log
`.trim()

            // When
            const ids = collectAllIds(flow)

            // Then
            expect(ids).toEqual(new Set(["my_flow", "schedule", "log", "on_error", "cleanup", "notify"]))
        })

        it("collects the ids nested inside a flowable branch", () => {
            // Given

            // When
            const ids = collectAllIds(FLOW_WITH_SWITCH)

            // Then
            expect(ids).toEqual(new Set(["my_flow", "sw", "prod_log", "dev_log", "default_log"]))
        })
    })

    describe("buildMinimalTask", () => {
        it("produces an object with the given type", () => {
            // Given

            // When
            const task = buildMinimalTask("io.kestra.plugin.core.log.Log")

            // Then
            expect(task.type).toBe("io.kestra.plugin.core.log.Log")
        })

        it("generates a clean id from the short class name", () => {
            // Given

            // When
            const task = buildMinimalTask("io.kestra.plugin.core.log.Log")

            // Then
            expect(task.id).toBe("log")
        })

        it("suffixes with an incrementing number when the base id is taken", () => {
            // Given
            const existingIds = new Set(["log", "log_1"])

            // When
            const task = buildMinimalTask("io.kestra.plugin.core.log.Log", existingIds)

            // Then
            expect(task.id).toBe("log_2")
        })

        it("inserted task produces valid YAML that parses correctly", () => {
            // Given
            const task = buildMinimalTask("io.kestra.plugin.core.flow.If")

            // When
            const result = addBlock(SIMPLE_FLOW, "tasks", task)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks).toHaveLength(3)
            expect(parsed.tasks[2].type).toBe("io.kestra.plugin.core.flow.If")
            expect(typeof parsed.tasks[2].id).toBe("string")
        })

        it("avoids id collisions against existing flow ids when existingIds is provided", () => {
            // Given — craft a flow that already owns the base id
            const collisionFlow = `
id: my_flow
namespace: company.team
tasks:
  - id: log
    type: io.kestra.plugin.core.log.Log
`.trim()
            const existingIds = new Set(["log"])

            // When — build with the same base to force a numeric suffix
            const task = buildMinimalTask("io.kestra.plugin.core.log.Log", existingIds)

            // Then — the generated id is distinct from every id in existingIds
            expect(existingIds.has(String(task.id))).toBe(false)
            expect(String(task.id)).not.toBe("")

            // And the resulting flow has no duplicate ids
            const result = addBlock(collisionFlow, "tasks", task)
            const parsed = flowYamlUtils.parse(result)
            const ids = parsed.tasks.map((t: Record<string, unknown>) => String(t.id))
            expect(new Set(ids).size).toBe(ids.length)
        })

        it("does not reuse an id already taken by another root section", () => {
            // Given — the same task type already sits in afterExecution
            const flow = `
id: my_flow
namespace: company.team
tasks:
  - id: task_a
    type: io.kestra.plugin.core.log.Log
afterExecution:
  - id: log
    type: io.kestra.plugin.core.log.Log
`.trim()

            // When
            const task = buildMinimalTask("io.kestra.plugin.core.log.Log", collectAllIds(flow))

            // Then
            expect(task.id).toBe("log_1")
        })

        it("does not reuse an id already taken by another trigger", () => {
            // Given

            // When
            const trigger = buildMinimalTask("io.kestra.plugin.core.trigger.Webhook", collectAllIds(FLOW_WITH_TRIGGERS))

            // Then
            expect(trigger.id).toBe("webhook_1")
        })
    })

    describe("groupValidationIssuesByTask", () => {
        it("groups a plain 'id.field: message' constraint under the task id", () => {
            const grouped = groupValidationIssuesByTask(["fetch_data.uri: must not be null"])

            expect(grouped.get("fetch_data")).toEqual(["uri: must not be null"])
        })

        it("parses a constraint behind a 'Validation error: ' prefix", () => {
            const grouped = groupValidationIssuesByTask(["Validation error: fetch_data.uri: must not be null"])

            expect(grouped.get("fetch_data")).toEqual(["uri: must not be null"])
        })

        it("keys a nested field path under its top-level task id", () => {
            const grouped = groupValidationIssuesByTask(["send.headers.Authorization: must not be blank"])

            expect(grouped.get("send")).toEqual(["headers.Authorization: must not be blank"])
        })

        it("collects several constraints for the same task", () => {
            const grouped = groupValidationIssuesByTask([
                "send.uri: must not be null",
                "send.method: must not be null",
            ])

            expect(grouped.get("send")).toEqual(["uri: must not be null", "method: must not be null"])
        })

        it("keys a flowable-level 'id: message' error (a DAG cycle) under the flowable id", () => {
            const grouped = groupValidationIssuesByTask(["Validation error: my_dag: Cyclic dependency detected: a, b"])

            expect(grouped.get("my_dag")).toEqual(["Cyclic dependency detected: a, b"])
        })

        it("keys a nested DAG sub-task constraint under the sub-task id (leading segment)", () => {
            const grouped = groupValidationIssuesByTask(["task1.task1.task.message: must not be null"])

            expect(grouped.get("task1")).toEqual(["task1.task.message: must not be null"])
        })

        it("splits a newline-joined multi-constraint entry into per-task issues", () => {
            const grouped = groupValidationIssuesByTask([
                "Validation error: log.log.task.message: must not be null\nlog_1.message: must not be null\na.a.task.message: must not be null\n",
            ])

            expect(grouped.get("log")).toEqual(["log.task.message: must not be null"])
            expect(grouped.get("log_1")).toEqual(["message: must not be null"])
            expect(grouped.get("a")).toEqual(["a.task.message: must not be null"])
        })

        it("skips flow-level errors that carry no 'id:' head", () => {
            const grouped = groupValidationIssuesByTask(["flow must not be empty"])

            expect(grouped.size).toBe(0)
        })

        it("returns an empty map for undefined input", () => {
            expect(groupValidationIssuesByTask(undefined).size).toBe(0)
        })

        describe("path-addressed constraints (errors / finally / afterExecution / nested)", () => {
            const flow = flowYamlUtils.parse(`
id: probe
namespace: qa
tasks:
  - id: if_task
    type: io.kestra.plugin.core.flow.If
    then:
      - id: nested_then
        type: io.kestra.plugin.core.log.Log
errors:
  - id: on_error
    type: io.kestra.plugin.core.log.Log
finally:
  - id: on_finally
    type: io.kestra.plugin.core.log.Log
afterExecution:
  - id: on_after
    type: io.kestra.plugin.core.log.Log
`.trim()) as Record<string, unknown>

            it("resolves an errors-section path to the task id", () => {
                const grouped = groupValidationIssuesByTask(["errors[0].message: must not be null"], flow)
                expect(grouped.get("on_error")).toEqual(["message: must not be null"])
            })

            it("resolves the underscore-prefixed _finally path to the task id", () => {
                const grouped = groupValidationIssuesByTask(["_finally[0].message: must not be null"], flow)
                expect(grouped.get("on_finally")).toEqual(["message: must not be null"])
            })

            it("resolves an afterExecution path to the task id", () => {
                const grouped = groupValidationIssuesByTask(["afterExecution[0].message: must not be null"], flow)
                expect(grouped.get("on_after")).toEqual(["message: must not be null"])
            })

            it("resolves a nested branch path to the nested task id", () => {
                const grouped = groupValidationIssuesByTask(["tasks[0].then[0].message: must not be null"], flow)
                expect(grouped.get("nested_then")).toEqual(["message: must not be null"])
            })

            it("ignores a path-addressed constraint when no flow is provided", () => {
                expect(groupValidationIssuesByTask(["errors[0].message: must not be null"]).size).toBe(0)
            })
        })
    })

    describe("round-trip safety: nested flowable content is preserved", () => {
        it("deleting a leaf task does not affect the flowable nested branches", () => {
            // Given

            // When
            const result = deleteBlock(FLOW_WITH_FLOWABLE, "tasks", "leaf_task")

            // Then
            const parsed = flowYamlUtils.parse(result)
            const ifTask = parsed.tasks.find((t: Record<string, unknown>) => t.id === "if_task")
            expect(ifTask).toBeDefined()
            expect(ifTask.then).toHaveLength(1)
            expect(ifTask.else).toHaveLength(1)
            expect(ifTask.then[0].id).toBe("nested_a")
            expect(ifTask.else[0].id).toBe("nested_b")
        })

        it("parse(stringify(x)) round-trip produces stable output", () => {
            // Given

            // When
            const parsed1 = flowYamlUtils.parse(FLOW_WITH_FLOWABLE)
            const stringified = flowYamlUtils.stringify(parsed1)
            const parsed2 = flowYamlUtils.parse(stringified)

            // Then
            expect(parsed2.tasks[1].then[0].id).toBe("nested_a")
            expect(parsed2.tasks[1].else[0].id).toBe("nested_b")
        })

        it("deeply nested structure is preserved through multiple ops", () => {
            // Given — 3 levels deep
            const deepFlow = `
id: my_flow
namespace: company.team
tasks:
  - id: outer_if
    type: io.kestra.plugin.core.flow.If
    condition: "{{ true }}"
    then:
      - id: inner_if
        type: io.kestra.plugin.core.flow.If
        condition: "{{ false }}"
        then:
          - id: deep_task
            type: io.kestra.plugin.core.log.Log
            message: Deep
`.trim()
            const newTask = {id: "deep_task_2", type: "io.kestra.plugin.core.log.Log"}

            // When
            const result = addBlockAtPath(deepFlow, "tasks[0].then[0].then", newTask)

            // Then
            const parsed = flowYamlUtils.parse(result)
            expect(parsed.tasks[0].then[0].then).toHaveLength(2)
            expect(parsed.tasks[0].then[0].then[1].id).toBe("deep_task_2")
        })
    })

    describe("DAG task wrapper ({task, dependsOn})", () => {
        describe("isWrappedLaneItem", () => {
            it("recognizes a {task, dependsOn} wrapper", () => {
                expect(isWrappedLaneItem({task: {id: "a", type: "io.kestra.plugin.core.log.Log"}, dependsOn: ["b"]})).toBe(true)
            })

            it("recognizes a wrapper with no dependsOn yet", () => {
                expect(isWrappedLaneItem({task: {id: "a", type: "io.kestra.plugin.core.log.Log"}})).toBe(true)
            })

            it("rejects a flat task (has its own type)", () => {
                expect(isWrappedLaneItem({id: "a", type: "io.kestra.plugin.core.log.Log"})).toBe(false)
            })

            it("rejects non-object values", () => {
                expect(isWrappedLaneItem(null)).toBe(false)
                expect(isWrappedLaneItem(["x"])).toBe(false)
                expect(isWrappedLaneItem("x")).toBe(false)
            })
        })

        describe("displayTaskOf / taskEditPathFor", () => {
            it("unwraps a wrapper to its inner task", () => {
                const wrapped = {task: {id: "a", type: "io.kestra.plugin.core.log.Log"}, dependsOn: ["b"]}
                expect(displayTaskOf(wrapped)).toEqual({id: "a", type: "io.kestra.plugin.core.log.Log"})
            })

            it("returns a flat task unchanged", () => {
                const flat = {id: "a", type: "io.kestra.plugin.core.log.Log"}
                expect(displayTaskOf(flat)).toBe(flat)
            })

            it("appends .task to the path for a wrapper", () => {
                const wrapped = {task: {id: "a", type: "io.kestra.plugin.core.log.Log"}}
                expect(taskEditPathFor("tasks[0].tasks[1]", wrapped)).toBe("tasks[0].tasks[1].task")
            })

            it("leaves the path unchanged for a flat task", () => {
                const flat = {id: "a", type: "io.kestra.plugin.core.log.Log"}
                expect(taskEditPathFor("tasks[1]", flat)).toBe("tasks[1]")
            })
        })

        describe("resolveBlockDomId", () => {
            it("resolves a wrapper's dom id off the wrapped task's id", () => {
                const items = [
                    {task: {id: "a", type: "io.kestra.plugin.core.log.Log"}},
                    {task: {id: "b", type: "io.kestra.plugin.core.log.Log"}, dependsOn: ["a"]},
                ]
                expect(resolveBlockDomId(items, 0)).toBe("a")
                expect(resolveBlockDomId(items, 1)).toBe("b")
            })

            it("disambiguates duplicate ids by appending the index to all but the first", () => {
                const items = [
                    {id: "dup", type: "io.kestra.plugin.core.log.Log"},
                    {id: "unique", type: "io.kestra.plugin.core.log.Log"},
                    {id: "dup", type: "io.kestra.plugin.core.log.Log"},
                ]
                expect(resolveBlockDomId(items, 0)).toBe("dup")
                expect(resolveBlockDomId(items, 1)).toBe("unique")
                expect(resolveBlockDomId(items, 2)).toBe("dup#2")
            })

            it("falls back to the index for an item with no id", () => {
                const items = [{type: "io.kestra.plugin.core.log.Log"}]
                expect(resolveBlockDomId(items, 0)).toBe("0")
            })
        })

        describe("isWrapperLane", () => {
            it("detects a DAG's tasks lane from its existing wrapped items", () => {
                expect(isWrapperLane(FLOW_WITH_DAG, "tasks[0].tasks")).toBe(true)
            })

            it("falls back to the parent's type for an empty Dag lane", () => {
                const emptyDag = `
id: my_flow
namespace: company.team
tasks:
  - id: my_dag
    type: io.kestra.plugin.core.flow.Dag
`.trim()
                expect(isWrapperLane(emptyDag, "tasks[0].tasks")).toBe(true)
            })

            it("returns false for a flat lane (e.g. Parallel.tasks)", () => {
                expect(isWrapperLane(FLOW_WITH_PARALLEL, "tasks[0].tasks")).toBe(false)
            })

            it("returns false for a flat, empty lane", () => {
                const emptyParallel = `
id: my_flow
namespace: company.team
tasks:
  - id: parallel_task
    type: io.kestra.plugin.core.flow.Parallel
`.trim()
                expect(isWrapperLane(emptyParallel, "tasks[0].tasks")).toBe(false)
            })
        })

        describe("wrapAsDagTask + addBlockAtPath", () => {
            it("inserts a new wrapped task into a DAG's tasks lane", () => {
                // Given
                const newTask = {id: "c", type: "io.kestra.plugin.core.log.Log", message: "C"}

                // When
                const result = addBlockAtPath(FLOW_WITH_DAG, "tasks[0].tasks", wrapAsDagTask(newTask))

                // Then
                const parsed = flowYamlUtils.parse<any>(result)
                expect(parsed.tasks[0].tasks).toHaveLength(3)
                expect(parsed.tasks[0].tasks[2].task.id).toBe("c")
                expect(parsed.tasks[0].tasks[2].dependsOn).toBeUndefined()
                // siblings untouched
                expect(parsed.tasks[0].tasks[1].dependsOn).toEqual(["a"])
            })
        })

        describe("extractBlockWithPath / updateBlockAtPath through .task", () => {
            it("reads the inner task's YAML via the .task suffix", () => {
                const extracted = flowYamlUtils.extractBlockWithPath({source: FLOW_WITH_DAG, path: "tasks[0].tasks[0].task"})
                const parsed = flowYamlUtils.parse<any>(extracted!)
                expect(parsed.id).toBe("a")
                expect(parsed.type).toBe("io.kestra.plugin.core.log.Log")
            })

            it("updates the inner task without disturbing the wrapper's dependsOn", () => {
                const updatedYaml = "id: b\ntype: io.kestra.plugin.core.log.Log\nmessage: Updated"

                const result = updateBlockAtPath(FLOW_WITH_DAG, "tasks[0].tasks[1].task", updatedYaml)

                const parsed = flowYamlUtils.parse<any>(result)
                expect(parsed.tasks[0].tasks[1].task.message).toBe("Updated")
                expect(parsed.tasks[0].tasks[1].dependsOn).toEqual(["a"])
                expect(parsed.tasks[0].tasks[0].task.id).toBe("a")
            })
        })

        describe("dependsOn persistence via replaceBlockWithPath", () => {
            it("sets dependsOn on a wrapper that has none yet", () => {
                const result = flowYamlUtils.replaceBlockWithPath({
                    source: FLOW_WITH_DAG,
                    path: "tasks[0].tasks[0].dependsOn",
                    newContent: flowYamlUtils.stringify(["b"]),
                })

                const parsed = flowYamlUtils.parse<any>(result)
                expect(parsed.tasks[0].tasks[0].dependsOn).toEqual(["b"])
                expect(parsed.tasks[0].tasks[0].task.id).toBe("a")
            })

            it("removes dependsOn when the new value is an empty array", () => {
                const result = flowYamlUtils.replaceBlockWithPath({
                    source: FLOW_WITH_DAG,
                    path: "tasks[0].tasks[1].dependsOn",
                    newContent: "",
                })

                const parsed = flowYamlUtils.parse<any>(result)
                expect(parsed.tasks[0].tasks[1].dependsOn).toBeUndefined()
                expect(parsed.tasks[0].tasks[1].task.id).toBe("b")
            })
        })

        describe("deleteBlockAtPath on a wrapper", () => {
            it("removes the whole {task, dependsOn} entry, not just the inner task", () => {
                const result = deleteBlockAtPath(FLOW_WITH_DAG, "tasks[0].tasks[0]")

                const parsed = flowYamlUtils.parse<any>(result)
                expect(parsed.tasks[0].tasks).toHaveLength(1)
                expect(parsed.tasks[0].tasks[0].task.id).toBe("b")
            })
        })

        describe("duplicateBlockAtPath on a wrapper", () => {
            it("duplicates the wrapper, renaming only the inner task's id", () => {
                const result = duplicateBlockAtPath(FLOW_WITH_DAG, "tasks[0].tasks[0]")

                const parsed = flowYamlUtils.parse<any>(result)
                expect(parsed.tasks[0].tasks).toHaveLength(3)
                const copy = parsed.tasks[0].tasks[1]
                expect(String(copy.task.id)).toMatch(/^a_copy/)
                expect(copy.task.type).toBe("io.kestra.plugin.core.log.Log")
                // untouched sibling still depends on the original "a", not the copy
                expect(parsed.tasks[0].tasks[2].dependsOn).toEqual(["a"])
            })

            it("preserves the duplicate's own dependsOn value", () => {
                const result = duplicateBlockAtPath(FLOW_WITH_DAG, "tasks[0].tasks[1]")

                const parsed = flowYamlUtils.parse<any>(result)
                const copy = parsed.tasks[0].tasks[2]
                expect(String(copy.task.id)).toMatch(/^b_copy/)
                expect(copy.dependsOn).toEqual(["a"])
            })
        })
    })
})
