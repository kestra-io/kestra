import {describe, it, expect} from "vitest"

import {isRootSectionPath, isTaskListPath, moveTaskOntoEdge, resolveTaskInsertionTarget, resolveTaskInsertionTargetInAnySection, sectionFromParentPath} from "../../../../../src/components/no-code/blocks/blockSections"
import * as flowYamlUtils from "@kestra-io/topology/flow-yaml-utils"

interface DagLaneItem {
    task: {id: string}
    dependsOn?: string[]
}

interface DagProbeFlow {
    tasks: {id: string; tasks?: DagLaneItem[]}[]
}

describe("blockSections", () => {
    const FLOW = `id: topology-insert
namespace: company.team
tasks:
  - id: first
    type: io.kestra.plugin.core.log.Log
    message: first
  - id: parent
    type: io.kestra.plugin.core.flow.Parallel
    tasks:
      - id: child
        type: io.kestra.plugin.core.log.Log
        message: child
    errors:
      - id: err1
        type: io.kestra.plugin.core.log.Log
        message: err1
  - id: last
    type: io.kestra.plugin.core.log.Log
    message: last
errors:
  - id: notify
    type: io.kestra.plugin.core.log.Log
    message: notify
`

    describe("isTaskListPath", () => {
        it.each([
            "tasks",
            "triggers",
            "errors",
            "finally",
            "afterExecution",
        ])("recognises the root %s lane", (section) => {
            // Given

            // When
            const result = isTaskListPath(section)

            // Then
            expect(result).toBe(true)
        })

        it.each([
            "tasks[0].tasks",
            "tasks[0].then",
            "tasks[0].else",
            "tasks[0].errors",
            "tasks[0].finally",
            "tasks[0].defaults",
            "tasks[1].cases.prod",
            "tasks[1].cases[\"with space\"]",
        ])("recognises the nested %s lane", (path) => {
            // Given

            // When
            const result = isTaskListPath(path)

            // Then
            expect(result).toBe(true)
        })

        it.each([
            "inputs",
            "outputs",
            "variables",
            "labels",
            "tasks[0].inputs",
            "concurrency",
        ])("rejects %s, which a schema-driven form owns", (path) => {
            // Given

            // When
            const result = isTaskListPath(path)

            // Then
            expect(result).toBe(false)
        })
    })

    describe("isRootSectionPath", () => {
        it("accepts a bare root section", () => {
            // Given

            // When / Then
            expect(isRootSectionPath("tasks")).toBe(true)
            expect(isRootSectionPath("triggers")).toBe(true)
            expect(isRootSectionPath("afterExecution")).toBe(true)
        })

        it("rejects a nested lane, which names itself rather than its section", () => {
            // Given

            // When / Then
            expect(isRootSectionPath("tasks[0].then")).toBe(false)
            expect(isRootSectionPath("tasks[0].cases.fast")).toBe(false)
            expect(isRootSectionPath("tasks[0].defaults")).toBe(false)
        })
    })

    describe("sectionFromParentPath", () => {
        it("maps a lane to the section that owns it", () => {
            // Given

            // When / Then
            expect(sectionFromParentPath("errors")).toBe("errors")
            expect(sectionFromParentPath("tasks[0].finally")).toBe("finally")
            expect(sectionFromParentPath("triggers")).toBe("triggers")
            expect(sectionFromParentPath("tasks[0].then")).toBe("tasks")
        })
    })

    describe("resolveTaskInsertionTarget", () => {
        it("resolves the first task in the root list", () => {
            // Given / When
            const target = resolveTaskInsertionTarget(FLOW, "tasks", "first")

            // Then
            expect(target).toEqual({parentPath: "tasks", refIndex: 0})
        })

        it("resolves the last task in the root list", () => {
            // Given / When
            const target = resolveTaskInsertionTarget(FLOW, "tasks", "last")

            // Then
            expect(target).toEqual({parentPath: "tasks", refIndex: 2})
        })

        it("resolves a task nested inside a flowable", () => {
            // Given / When
            const target = resolveTaskInsertionTarget(FLOW, "tasks", "child")

            // Then
            expect(target).toEqual({parentPath: "tasks[1].tasks", refIndex: 0})
        })

        it("resolves a task nested inside an errors lane", () => {
            // Given / When
            const target = resolveTaskInsertionTarget(FLOW, "tasks", "err1")

            // Then
            expect(target).toEqual({parentPath: "tasks[1].errors", refIndex: 0})
        })

        it("returns undefined when the task id can't be found", () => {
            // Given / When
            const target = resolveTaskInsertionTarget(FLOW, "tasks", "does-not-exist")

            // Then
            expect(target).toBeUndefined()
        })

        it("does not reach the flow-level errors section from the tasks section", () => {
            // Given / When
            const target = resolveTaskInsertionTarget(FLOW, "tasks", "notify")

            // Then
            expect(target).toBeUndefined()
        })
    })


    describe("moveTaskOntoEdge", () => {
        const SEQUENTIAL = `id: seq
namespace: qa
tasks:
  - id: a
    type: io.kestra.plugin.core.log.Log
    message: a
  - id: b
    type: io.kestra.plugin.core.log.Log
    message: b
  - id: c
    type: io.kestra.plugin.core.log.Log
    message: c
`

        const DAG = `id: dag
namespace: qa
tasks:
  - id: pipeline
    type: io.kestra.plugin.core.flow.Dag
    tasks:
      - task:
          id: fetch
          type: io.kestra.plugin.core.log.Log
          message: fetch
      - task:
          id: middle
          type: io.kestra.plugin.core.log.Log
          message: middle
        dependsOn:
          - fetch
      - task:
          id: sink
          type: io.kestra.plugin.core.log.Log
          message: sink
        dependsOn:
          - middle
      - task:
          id: spare
          type: io.kestra.plugin.core.log.Log
          message: spare
`
        const idsOf = (source: string) =>
            flowYamlUtils.parse<DagProbeFlow>(source)!.tasks.map(task => task.id)

        const dagOf = (source: string) => {
            const lane = flowYamlUtils.parse<DagProbeFlow>(source)!.tasks[0]!.tasks ?? []
            return Object.fromEntries(lane.map(item => [item.task.id, item.dependsOn ?? null]))
        }

        it("reorders a sequential lane", () => {
            const next = moveTaskOntoEdge(SEQUENTIAL, "c", {refId: "a", position: "before"})

            expect(idsOf(next)).toEqual(["c", "a", "b"])
        })

        it("is a no-op when dropped on itself", () => {
            expect(moveTaskOntoEdge(SEQUENTIAL, "b", {refId: "b", position: "after"})).toBe(SEQUENTIAL)
        })

        it("heals the chain it leaves and splices into the one it joins", () => {
            const next = moveTaskOntoEdge(DAG, "middle", {
                refId: "spare",
                position: "after",
                dagDependency: {fromId: "spare"},
            })

            expect(dagOf(next)).toEqual({
                fetch: null,
                // sink inherited middle's upstream instead of being orphaned
                sink: ["fetch"],
                spare: null,
                middle: ["spare"],
            })
        })

        it("drops a stale dependsOn when the task leaves the dag", () => {
            const next = moveTaskOntoEdge(DAG, "middle", {refId: "pipeline", position: "after"})

            expect(idsOf(next)).toEqual(["pipeline", "middle"])
            expect(dagOf(next)).toEqual({fetch: null, sink: ["fetch"], spare: null})
            const moved = flowYamlUtils.parse<DagProbeFlow>(next)!.tasks[1]! as DagLaneItem & {id: string}
            expect(moved.dependsOn).toBeUndefined()
            expect(moved.id).toBe("middle")
        })

        it("is a no-op when dropped on an edge it already ends, keeping its dependsOn", () => {
            const onOwnOutgoing = moveTaskOntoEdge(DAG, "middle", {
                refId: "sink",
                position: "before",
                dagDependency: {fromId: "middle", toId: "sink"},
            })
            const onOwnIncoming = moveTaskOntoEdge(DAG, "middle", {
                refId: "middle",
                position: "before",
                dagDependency: {fromId: "fetch", toId: "middle"},
            })

            expect(onOwnOutgoing).toBe(DAG)
            expect(onOwnIncoming).toBe(DAG)
        })

        it("leaves the source alone when the target id does not exist", () => {
            expect(moveTaskOntoEdge(SEQUENTIAL, "a", {refId: "nope", position: "after"})).toBe(SEQUENTIAL)
        })
    })

    describe("resolveTaskInsertionTargetInAnySection", () => {
        it("resolves a task in the tasks section", () => {
            // Given / When
            const target = resolveTaskInsertionTargetInAnySection(FLOW, "first")

            // Then
            expect(target).toEqual({parentPath: "tasks", refIndex: 0, section: "tasks"})
        })

        it("resolves a task in the flow-level errors section, which the topology edge only knows by id", () => {
            // Given / When
            const target = resolveTaskInsertionTargetInAnySection(FLOW, "notify")

            // Then
            expect(target).toEqual({parentPath: "errors", refIndex: 0, section: "errors"})
        })

        it("keeps resolving a task nested in a flowable lane under its owning section", () => {
            // Given / When
            const target = resolveTaskInsertionTargetInAnySection(FLOW, "child")

            // Then
            expect(target).toEqual({parentPath: "tasks[1].tasks", refIndex: 0, section: "tasks"})
        })

        it("returns undefined when no section holds the id", () => {
            // Given / When
            const target = resolveTaskInsertionTargetInAnySection(FLOW, "does-not-exist")

            // Then
            expect(target).toBeUndefined()
        })

        it("ignores triggers, which may legally reuse a task id", () => {
            // Given
            const flowWithCollidingTrigger = `id: id-collision
namespace: company.team
tasks:
  - id: ok
    type: io.kestra.plugin.core.log.Log
    message: ok
errors:
  - id: nightly
    type: io.kestra.plugin.core.log.Log
    message: notify
triggers:
  - id: nightly
    type: io.kestra.plugin.core.trigger.Schedule
    cron: "0 3 * * *"
`

            // When
            const target = resolveTaskInsertionTargetInAnySection(flowWithCollidingTrigger, "nightly")

            // Then
            expect(target).toEqual({parentPath: "errors", refIndex: 0, section: "errors"})
        })
    })
})
