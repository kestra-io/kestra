import {describe, it, expect} from "vitest"

import {computeDagLayout} from "../../../../src/components/dependencies/utils/dagLayout"

const STAGING = ["db.staging.stg_orders", "db.staging.stg_customers", "db.staging.stg_payments"]
const MART = "db.marts.fct_orders"
const FLOW = "dbt.demo_dbt_lineage"

const fanIn = {
    nodes: [...STAGING, MART, FLOW],
    edges: [
        ...STAGING.map((source) => ({source, target: MART})),
        {source: FLOW, target: MART},
    ],
}

describe("computeDagLayout", () => {
    it("ranks nodes by longest path from the roots", () => {
        const {positions, columns} = computeDagLayout(fanIn.nodes, fanIn.edges)

        expect(columns).toHaveLength(2)
        expect(columns[0]).toHaveLength(4)
        expect(columns[1]).toEqual([MART])
        STAGING.forEach((id) => expect(positions.get(id)!.rank).toBe(0))
        expect(positions.get(MART)!.rank).toBe(1)
    })

    it("gives every node in a rank the same x and distinct y", () => {
        const {positions} = computeDagLayout(fanIn.nodes, fanIn.edges)
        const roots = fanIn.nodes.filter((id) => positions.get(id)!.rank === 0)

        expect(new Set(roots.map((id) => positions.get(id)!.x)).size).toBe(1)
        expect(new Set(roots.map((id) => positions.get(id)!.y)).size).toBe(roots.length)
        expect(positions.get(MART)!.x).toBeGreaterThan(positions.get(STAGING[0])!.x)
    })

    it("produces identical coordinates whatever the input order", () => {
        const forward = computeDagLayout(fanIn.nodes, fanIn.edges)
        const reversed = computeDagLayout([...fanIn.nodes].reverse(), [...fanIn.edges].reverse())

        expect([...reversed.positions.entries()].sort()).toEqual([...forward.positions.entries()].sort())
    })

    it("takes the longest path when a node is reachable by two routes", () => {
        const {positions} = computeDagLayout(
            ["a", "b", "c"],
            [{source: "a", target: "b"}, {source: "b", target: "c"}, {source: "a", target: "c"}],
        )

        expect(positions.get("c")!.rank).toBe(2)
    })

    it("lays out a cyclic graph instead of stalling", () => {
        const {positions} = computeDagLayout(
            ["a", "b", "c"],
            [{source: "a", target: "b"}, {source: "b", target: "c"}, {source: "c", target: "a"}],
        )

        expect(positions.size).toBe(3)
    })

    it("ignores self edges and edges pointing outside the node set", () => {
        const {positions, columns} = computeDagLayout(
            ["a", "b"],
            [{source: "a", target: "a"}, {source: "a", target: "b"}, {source: "b", target: "ghost"}],
        )

        expect(columns).toEqual([["a"], ["b"]])
        expect(positions.get("b")!.rank).toBe(1)
    })

    it("centres each column vertically around the origin", () => {
        const {positions} = computeDagLayout(["a", "b", "c"], [])
        const ys = ["a", "b", "c"].map((id) => positions.get(id)!.y)

        expect(ys.reduce((sum, y) => sum + y, 0)).toBe(0)
    })
})
