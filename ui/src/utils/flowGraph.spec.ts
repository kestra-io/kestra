import {describe, expect, it} from "vitest"
import {toFlowGraph} from "./flowGraph"

describe("toFlowGraph", () => {
    it("fills in the collections a graph leaves out", () => {
        expect(toFlowGraph({})).toEqual({nodes: [], clusters: [], edges: []})
    })

    it("keeps the nodes, edges and clusters it is given", () => {
        const graph = {
            nodes: [{uid: "a", type: "io.kestra.core.models.hierarchies.GraphTask"}],
            edges: [{source: "a", target: "b"}],
            clusters: [{cluster: {uid: "c", type: "cluster"}, nodes: ["a"], parents: [], start: "a", end: "a"}],
        }

        const converted = toFlowGraph(graph)

        expect(converted.nodes).toEqual(graph.nodes)
        expect(converted.edges).toEqual(graph.edges)
        expect(converted.clusters).toEqual(graph.clusters)
    })
})
