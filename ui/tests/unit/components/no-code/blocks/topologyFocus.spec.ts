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
