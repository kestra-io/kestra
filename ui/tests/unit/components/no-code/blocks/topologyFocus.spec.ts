import {describe, it, expect} from "vitest"

import {
    buildTopologyFocusOrder,
    firstChildOf,
    moveWithinSiblings,
    parentOf,
} from "../../../../../src/components/no-code/blocks/topologyFocus"
import {duplicateBlockAtPath, moveBlockAtPath} from "../../../../../src/utils/flowableBlockOps"

describe("topologyFocus", () => {
    const FLOW = `id: nav
namespace: company.team
tasks:
  - id: extract
    type: io.kestra.plugin.core.log.Log
    message: extract
  - id: route
    type: io.kestra.plugin.core.flow.If
    condition: "true"
    then:
      - id: sync_eu
        type: io.kestra.plugin.core.log.Log
        message: eu
      - id: audit_eu
        type: io.kestra.plugin.core.log.Log
        message: audit
  - id: pipeline
    type: io.kestra.plugin.core.flow.Dag
    tasks:
      - task:
          id: fetch
          type: io.kestra.plugin.core.log.Log
          message: fetch
      - task:
          id: join
          type: io.kestra.plugin.core.log.Log
          message: join
        dependsOn:
          - fetch
  - id: publish
    type: io.kestra.plugin.core.log.Log
    message: publish
errors:
  - id: notify
    type: io.kestra.plugin.core.log.Log
    message: notify
`

    it("flattens depth-first so a flowable is followed by its own children", () => {
        const order = buildTopologyFocusOrder(FLOW)

        expect(order.map(entry => entry.id)).toEqual([
            "extract", "route", "sync_eu", "audit_eu", "pipeline", "fetch", "join", "publish", "notify",
        ])
    })

    it("records the lane each node belongs to and its depth", () => {
        const order = buildTopologyFocusOrder(FLOW)
        const byId = Object.fromEntries(order.map(entry => [entry.id, entry]))

        expect(byId.extract).toMatchObject({parentPath: "tasks", depth: 0})
        expect(byId.sync_eu).toMatchObject({parentPath: "tasks[1].then", depth: 1})
        expect(byId.fetch).toMatchObject({parentPath: "tasks[2].tasks", depth: 1})
        expect(byId.notify).toMatchObject({parentPath: "errors", depth: 0})
    })

    it("moves between siblings without escaping the lane", () => {
        const order = buildTopologyFocusOrder(FLOW)

        // Inside the If's `then` lane there are only two tasks.
        expect(moveWithinSiblings(order, "sync_eu", 1)).toBe("audit_eu")
        expect(moveWithinSiblings(order, "audit_eu", 1)).toBe("audit_eu")
        expect(moveWithinSiblings(order, "sync_eu", -1)).toBe("sync_eu")

        // The root lane skips over the nested children.
        expect(moveWithinSiblings(order, "route", 1)).toBe("pipeline")
        expect(moveWithinSiblings(order, "pipeline", -1)).toBe("route")
    })

    it("starts at the first node when nothing is focused", () => {
        const order = buildTopologyFocusOrder(FLOW)

        expect(moveWithinSiblings(order, undefined, 1)).toBe("extract")
    })

    it("steps into a flowable and back out of it", () => {
        const order = buildTopologyFocusOrder(FLOW)

        expect(firstChildOf(order, "route")).toBe("sync_eu")
        expect(firstChildOf(order, "pipeline")).toBe("fetch")
        expect(firstChildOf(order, "extract")).toBeUndefined()

        expect(parentOf(order, "sync_eu")).toBe("route")
        expect(parentOf(order, "join")).toBe("pipeline")
        expect(parentOf(order, "extract")).toBeUndefined()
    })

    const BRANCHED = `id: branched
namespace: company.team
tasks:
  - id: gate
    type: io.kestra.plugin.core.flow.If
    condition: "true"
    then:
      - id: in_then
        type: io.kestra.plugin.core.log.Log
        message: then
    else:
      - id: in_else
        type: io.kestra.plugin.core.log.Log
        message: else
  - id: router
    type: io.kestra.plugin.core.flow.Switch
    value: "{{ trigger.value }}"
    cases:
      a:
        - id: in_case_a
          type: io.kestra.plugin.core.log.Log
          message: a
      b:
        - id: in_case_b
          type: io.kestra.plugin.core.log.Log
          message: b
  - id: seq
    type: io.kestra.plugin.core.flow.Sequential
    tasks:
      - id: child
        type: io.kestra.plugin.core.log.Log
        message: child
    errors:
      - id: recover
        type: io.kestra.plugin.core.log.Log
        message: recover
`

    it("walks every branch of a flowable, not just the first non-empty one", () => {
        const order = buildTopologyFocusOrder(BRANCHED)

        expect(order.map(entry => entry.id)).toEqual([
            "gate", "in_then", "in_else",
            "router", "in_case_a", "in_case_b",
            "seq", "child", "recover",
        ])
    })

    it("keeps each branch its own sibling lane", () => {
        const order = buildTopologyFocusOrder(BRANCHED)
        const byId = Object.fromEntries(order.map(entry => [entry.id, entry]))

        expect(byId.in_then).toMatchObject({parentPath: "tasks[0].then", depth: 1})
        expect(byId.in_else).toMatchObject({parentPath: "tasks[0].else", depth: 1})
        expect(byId.in_case_a).toMatchObject({parentPath: "tasks[1].cases.a", depth: 1})
        expect(byId.in_case_b).toMatchObject({parentPath: "tasks[1].cases.b", depth: 1})
        expect(byId.recover).toMatchObject({parentPath: "tasks[2].errors", depth: 1})

        expect(moveWithinSiblings(order, "in_then", 1)).toBe("in_then")
        expect(moveWithinSiblings(order, "child", 1)).toBe("child")
    })

    it("steps out of any branch back to the flowable that owns it", () => {
        const order = buildTopologyFocusOrder(BRANCHED)

        expect(parentOf(order, "in_else")).toBe("gate")
        expect(parentOf(order, "in_case_b")).toBe("router")
        expect(parentOf(order, "recover")).toBe("seq")
        expect(firstChildOf(order, "gate")).toBe("in_then")
    })

    // `Duplicate` in the node menu resolves the task through this order, so a task the walk never
    // reaches gets a menu entry that silently does nothing.
    it("hands a task in an else, a second case or an errors lane a resolvable path", () => {
        const order = buildTopologyFocusOrder(BRANCHED)
        const byId = Object.fromEntries(order.map(entry => [entry.id, entry]))

        for (const id of ["in_else", "in_case_b", "recover"]) {
            expect(duplicateBlockAtPath(BRANCHED, byId[id].path), `duplicate ${id}`).not.toBe(BRANCHED)
        }
    })

    const DOTTED_CASES = `id: dotted
namespace: company.team
tasks:
  - id: router
    type: io.kestra.plugin.core.flow.Switch
    value: "{{ trigger.version }}"
    cases:
      "1.0":
        - id: legacy_path
          type: io.kestra.plugin.core.log.Log
          message: legacy
      "2.0":
        - id: current_path
          type: io.kestra.plugin.core.log.Log
          message: current
`

    it("reaches a Switch case whose key contains a dot", () => {
        const order = buildTopologyFocusOrder(DOTTED_CASES)

        expect(order.map(entry => entry.id)).toEqual(["router", "legacy_path", "current_path"])
    })

    it("hands a dotted-case task a path the block operations can resolve", () => {
        const order = buildTopologyFocusOrder(DOTTED_CASES)
        const byId = Object.fromEntries(order.map(entry => [entry.id, entry]))

        for (const id of ["legacy_path", "current_path"]) {
            expect(duplicateBlockAtPath(DOTTED_CASES, byId[id].path), `duplicate ${id}`).not.toBe(DOTTED_CASES)
        }
        expect(parentOf(order, "legacy_path")).toBe("router")
    })

    it("reaches a Switch case keyed by anything the path grammar has to quote", () => {
        const flow = `id: hostile
namespace: company.team
tasks:
  - id: router
    type: io.kestra.plugin.core.flow.Switch
    value: "{{ trigger.value }}"
    cases:
      'a.b[0]':
        - id: bracketed
          type: io.kestra.plugin.core.log.Log
      'with space':
        - id: spaced
          type: io.kestra.plugin.core.log.Log
`
        const order = buildTopologyFocusOrder(flow)
        const byId = Object.fromEntries(order.map(entry => [entry.id, entry]))

        expect(order.map(entry => entry.id)).toEqual(["router", "bracketed", "spaced"])
        for (const id of ["bracketed", "spaced"]) {
            expect(duplicateBlockAtPath(flow, byId[id].path), `duplicate ${id}`).not.toBe(flow)
        }
    })

    it("skips an empty branch rather than counting it as a lane", () => {
        const flow = `id: empty
namespace: company.team
tasks:
  - id: gate
    type: io.kestra.plugin.core.flow.If
    condition: "true"
    then: []
    else:
      - id: only_else
        type: io.kestra.plugin.core.log.Log
`
        const order = buildTopologyFocusOrder(flow)

        expect(order.map(entry => entry.id)).toEqual(["gate", "only_else"])
        expect(firstChildOf(order, "gate")).toBe("only_else")
    })

    it("returns nothing for a source that does not parse into a flow", () => {
        expect(buildTopologyFocusOrder("")).toEqual([])
    })

    // The canvas actions address a task by the path this walk hands them, and both helpers return
    // the source untouched on a path they cannot resolve — so a drift here is a silent no-op.
    it("hands every focusable task a path the block operations can resolve", () => {
        const order = buildTopologyFocusOrder(FLOW)
        expect(order.length).toBeGreaterThan(5)

        for (const entry of order) {
            expect(duplicateBlockAtPath(FLOW, entry.path), `duplicate ${entry.id}`).not.toBe(FLOW)
        }

        // A lone task in its lane cannot move, so only the ones with a sibling are asserted.
        const withSibling = order.filter(
            entry => order.filter(other => other.parentPath === entry.parentPath).length > 1,
        )
        expect(withSibling.length).toBeGreaterThan(0)
        for (const entry of withSibling) {
            const moved = ["up", "down"] as const
            expect(
                moved.some(direction => moveBlockAtPath(FLOW, entry.path, direction) !== FLOW),
                `reorder ${entry.id}`,
            ).toBe(true)
        }
    })
})
