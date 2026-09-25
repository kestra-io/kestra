import {describe, expect, it} from "vitest"
import {computeDagLayout} from "./dagLayout"

const COLUMN_GAP = 360
const ROW_GAP = 96

const layout = (ids: string[], edges: {source: string; target: string}[], flows: string[] = []) => {
    const produced = new Set(edges.map((edge) => edge.source))
    return computeDagLayout(ids, edges, {
        columnGap: COLUMN_GAP,
        rowGap: ROW_GAP,
        // DagCanvas.vue's own predicate: a flow leads the graph only when it produces something.
        ownColumn: (id) => flows.includes(id) && produced.has(id),
    })
}

/** Node ids grouped into their rendered columns, left to right, each column sorted for a stable assertion. */
const columns = (positions: Map<string, {x: number; y: number}>): string[][] => {
    const byX = new Map<number, string[]>()
    positions.forEach(({x}, id) => byX.set(x, [...(byX.get(x) ?? []), id]))
    return [...byX.keys()].sort((a, b) => a - b).map((x) => byX.get(x)!.sort())
}

const backwards = (
    positions: Map<string, {x: number; y: number}>,
    edges: {source: string; target: string}[],
): string[] =>
    edges
        .filter(({source, target}) => positions.get(source)!.x > positions.get(target)!.x)
        .map(({source, target}) => `${source} -> ${target}`)

/**
 * The kestra-ee#11231 reproduction, read from `asset_topologies` after running `dbt.demo/dbt_lineage`
 * against github.com/soyasis/dbt-tests. The real ids matter: the bug only surfaced because the flow's
 * uid sorts after the asset ids, so tidier names would let this pass against the unfixed layout.
 */
const DBT = (() => {
    const asset = (schema: string, name: string) => `angelic-lattice-394708.demo_data_${schema}.${name}`
    const flow = "dbt_dbt.demo_dbt_lineage"

    const seeds = ["raw_customers", "raw_orders", "raw_payments", "raw_products"].map((name) => asset("raw", name))
    const orphanSeed = asset("raw", "order_status_mapping")
    const staging = ["stg_customers", "stg_orders", "stg_payments", "stg_products"].map((name) => asset("staging", name))
    const marts = ["dim_customers", "dim_products", "fct_orders"].map((name) => asset("marts", name))
    const assets = [...seeds, orphanSeed, ...staging, ...marts]

    const lineage = [
        // Each seed feeds its own staging model.
        ...seeds.map((seed, index) => ({source: seed, target: staging[index]})),
        // dim_customers and fct_orders read customers, orders and payments; dim_products reads orders and products.
        ...["stg_customers", "stg_orders", "stg_payments"].map((name) => ({
            source: asset("staging", name),
            target: asset("marts", "dim_customers"),
        })),
        ...["stg_orders", "stg_products"].map((name) => ({
            source: asset("staging", name),
            target: asset("marts", "dim_products"),
        })),
        ...staging.map((name) => ({source: name, target: asset("marts", "fct_orders")})),
    ]

    // The flow writes every asset and reads the seeds and the staging models: one two-cycle per asset it does both to.
    const flowEdges = [
        ...assets.map((id) => ({source: flow, target: id})),
        ...[...seeds, ...staging].map((id) => ({source: id, target: flow})),
    ]

    return {flow, seeds, orphanSeed, staging, marts, assets, lineage, flowEdges}
})()

describe("computeDagLayout", () => {
    it("should rank dbt seeds before staging models before the marts that consume them", () => {
        const positions = layout([...DBT.assets, DBT.flow], [...DBT.lineage, ...DBT.flowEdges], [DBT.flow])

        expect(columns(positions)).toEqual([
            [DBT.flow],
            [DBT.orphanSeed, ...DBT.seeds].sort(),
            [...DBT.staging].sort(),
            [...DBT.marts].sort(),
        ])
        expect(backwards(positions, DBT.lineage)).toEqual([])
    })

    it("should rank the same regardless of where the flow id sorts against the asset ids", () => {
        const flow = "aaa_flow_sorting_first"
        const edges = [
            ...DBT.lineage,
            ...DBT.flowEdges.map(({source, target}) => ({
                source: source === DBT.flow ? flow : source,
                target: target === DBT.flow ? flow : target,
            })),
        ]
        const positions = layout([...DBT.assets, flow], edges, [flow])

        expect(columns(positions)).toEqual([
            [flow],
            [DBT.orphanSeed, ...DBT.seeds].sort(),
            [...DBT.staging].sort(),
            [...DBT.marts].sort(),
        ])
    })

    it("should rank a consumer-only flow after the assets it reads", () => {
        const edges = [
            {source: "raw", target: "stg"},
            {source: "stg", target: "reader_flow"},
        ]
        const positions = layout(["raw", "stg", "reader_flow"], edges, ["reader_flow"])

        expect(columns(positions)).toEqual([["raw"], ["stg"], ["reader_flow"]])
    })

    it("should keep the depth of a chain that runs only through flows", () => {
        const edges = [
            {source: "load_flow", target: "raw_table"},
            {source: "raw_table", target: "transform_flow"},
            {source: "transform_flow", target: "derived_table"},
        ]
        const ids = ["raw_table", "derived_table", "load_flow", "transform_flow"]
        const positions = layout(ids, edges, ["load_flow", "transform_flow"])

        // Two producing flows lead, and the tables keep one column each: dropping every edge of a pinned
        // node would collapse them into one, losing lineage the graph does carry.
        expect(columns(positions)).toEqual([["load_flow", "transform_flow"], ["raw_table"], ["derived_table"]])
    })

    it("should return identical coordinates for the same graph whatever order the nodes arrive in", () => {
        const ids = [...DBT.assets, DBT.flow]
        const edges = [...DBT.lineage, ...DBT.flowEdges]

        const first = layout(ids, edges, [DBT.flow])
        const shuffled = layout([...ids].reverse(), [...edges].reverse(), [DBT.flow])

        expect(columns(shuffled)).toEqual(columns(first))
        ids.forEach((id) => expect(shuffled.get(id)).toEqual(first.get(id)))
    })

    it("should place every node in lineage order when two assets form a cycle", () => {
        const edges = [
            {source: "table_a", target: "table_b"},
            {source: "table_b", target: "table_a"},
            {source: "table_b", target: "table_c"},
        ]
        const positions = layout(["table_a", "table_b", "table_c"], edges)

        // The cycle is cut at the lowest id, so table_a leads; table_c stays after the table_b it reads.
        expect(columns(positions)).toEqual([["table_a"], ["table_b"], ["table_c"]])
    })

    it("should rank in lineage order when a flow closes a cycle longer than two nodes", () => {
        // The flow writes the seed and the staging model, then reads the mart built from them. No two-node
        // cycle exists, so the edge drop above does not fire and the rank loop hits its arbitrary cut.
        const flow = "check_flow"
        const edges = [
            {source: flow, target: "raw_x"},
            {source: flow, target: "stg_x"},
            {source: "raw_x", target: "stg_x"},
            {source: "stg_x", target: "fct_x"},
            {source: "fct_x", target: flow},
        ]
        const positions = layout(["raw_x", "stg_x", "fct_x", flow], edges, [flow])

        expect(columns(positions)).toEqual([[flow], ["raw_x"], ["stg_x"], ["fct_x"]])
    })
})
