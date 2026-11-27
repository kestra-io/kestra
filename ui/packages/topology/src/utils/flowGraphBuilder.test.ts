import {describe, expect, test} from "vitest"
import {parse} from "./flowYamlUtils"
import {buildFlowGraph, isSupportedFlowableType, unsupportedFlowableTypes} from "./flowGraphBuilder"
import type {FlowGraph, MinimalNode} from "./vueFlowUtils"
import coreFlowableTypes from "./flowGraphFixtures/coreFlowableTypes.json"

type ContractGraph = ReturnType<typeof contractView>

// Generated from the backend builder by FlowGraphContractTest.java, which fails when one is stale. Never edit one by hand.
const fixtures = import.meta.glob<{source: string; graph: ContractGraph}>(
    "./flowGraphFixtures/flows/*.json",
    {eager: true, import: "default"},
)

const POINT_TYPES = ["GraphClusterRoot", "GraphClusterEnd", "GraphClusterFinally", "GraphClusterAfterExecution"]

function sortedBy<T>(items: T[], key: (item: T) => string): T[] {
    return [...items].sort((a, b) => (key(a) < key(b) ? -1 : key(a) > key(b) ? 1 : 0))
}

/** Mirrors `contractView` in FlowGraphContractTest.java; the package README lists what it leaves out, and why. */
function contractView(graph: Pick<FlowGraph, "nodes" | "edges" | "clusters">) {
    const stableUids = new Map(graph.nodes.map((node) => [
        node.uid,
        POINT_TYPES.some((type) => node.type.endsWith(`.${type}`)) ? node.uid.replace(/-[A-Za-z0-9]+$/, "-*") : node.uid,
    ]))
    const uidOf = (uid: string) => stableUids.get(uid) ?? uid
    const reference = (value?: {id?: unknown; type?: unknown}) => value && {id: value.id, type: value.type}
    const nodeView = (node: MinimalNode) => ({
        uid: uidOf(node.uid),
        type: node.type,
        branchType: node.branchType,
        relationType: node.relationType,
        disabled: node.disabled,
        task: reference(node.task),
        triggerDeclaration: reference(node.triggerDeclaration as {id?: unknown; type?: unknown} | undefined),
    })

    return {
        nodes: sortedBy(graph.nodes.map(nodeView), (node) => node.uid),
        edges: sortedBy(graph.edges.map((edge) => ({
            source: uidOf(edge.source),
            target: uidOf(edge.target),
            relation: {relationType: edge.relation?.relationType, value: edge.relation?.value},
        })), (edge) => `${edge.source} ${edge.target}`),
        clusters: sortedBy((graph.clusters ?? []).map((cluster) => ({
            cluster: {
                uid: cluster.cluster.uid,
                type: cluster.cluster.type,
                branchType: cluster.cluster.branchType,
                relationType: cluster.cluster.relationType,
                taskNode: cluster.cluster.taskNode ? nodeView(cluster.cluster.taskNode as MinimalNode) : undefined,
            },
            nodes: cluster.nodes.map(uidOf).sort(),
            parents: cluster.parents,
            start: uidOf(cluster.start),
            end: uidOf(cluster.end),
        })), (cluster) => cluster.start),
    }
}

describe("buildFlowGraph, against the backend builder", () => {
    test("has fixtures to compare with", () => {
        expect(Object.keys(fixtures).length).toBeGreaterThan(20)
    })

    test.each(Object.entries(fixtures))("%s", (_path, fixture) => {
        const built = contractView(buildFlowGraph(parse(fixture.source)))

        expect(built).toEqual(contractView(fixture.graph as FlowGraph))
    })

    test("knows the subgraph of every flowable of the core plugin", () => {
        expect(coreFlowableTypes.filter((type) => !isSupportedFlowableType(type))).toEqual([])
    })
})

describe("buildFlowGraph, where the client is on its own", () => {
    const PLUGIN_FLOWABLE = "io.kestra.plugin.acme.Retry"
    const flow = (tasks: string) => parse(`id: flow\nnamespace: company.team\ntasks:\n${tasks}`)

    test("draws a flowable shipped by a plugin as a plain task, and reports it", () => {
        const source = flow(`
  - id: retry
    type: ${PLUGIN_FLOWABLE}
    tasks:
      - id: child
        type: io.kestra.plugin.core.log.Log`)

        const graph = buildFlowGraph(source)

        expect(graph.clusters).toEqual([])
        expect(graph.nodes.filter((node) => node.task).map((node) => node.uid)).toEqual(["root.retry"])
        expect(unsupportedFlowableTypes(source, (type) => type === PLUGIN_FLOWABLE)).toEqual([PLUGIN_FLOWABLE])
    })

    test("reports no flowable when the flow only nests the core ones", () => {
        const source = parse(fixtures["./flowGraphFixtures/flows/flowables.json"].source)

        expect(unsupportedFlowableTypes(source, (type) => coreFlowableTypes.includes(type))).toEqual([])
    })

    test("leaves out a task still being typed and keeps the others linked to the end", () => {
        const graph = buildFlowGraph(flow(`
  - id: first
    type: io.kestra.plugin.core.log.Log
  - id: second`))

        const end = graph.nodes.find((node) => node.type.endsWith("GraphClusterEnd"))
        expect(graph.nodes.some((node) => node.uid === "root.second")).toBe(false)
        expect(graph.edges).toContainEqual({source: "root.first", target: end?.uid, relation: {}})
    })

    test("gives the same uids on every build, so a lane's dots survive an edit", () => {
        const source = fixtures["./flowGraphFixtures/flows/sequential.json"].source

        expect(buildFlowGraph(parse(source))).toEqual(buildFlowGraph(parse(source)))
    })

    test("keeps parallel branches in the order of the flow", () => {
        const graph = buildFlowGraph(parse(fixtures["./flowGraphFixtures/flows/parallel.json"].source))

        const branches = graph.edges
            .filter((edge) => edge.relation?.relationType === "PARALLEL")
            .map((edge) => edge.target.replace(/.*\./, ""))
        expect(branches).toEqual(["t1", "t2", "t3", "t4", "t5", "t6"])
    })

    test("throws when the tasks of a Dag depend on each other in a cycle", () => {
        const source = flow(`
  - id: dag
    type: io.kestra.plugin.core.flow.Dag
    tasks:
      - task:
          id: a
          type: io.kestra.plugin.core.log.Log
        dependsOn: [b]
      - task:
          id: b
          type: io.kestra.plugin.core.log.Log
        dependsOn: [a]`)

        expect(() => buildFlowGraph(source)).toThrow("Dag 'dag'")
    })
})
