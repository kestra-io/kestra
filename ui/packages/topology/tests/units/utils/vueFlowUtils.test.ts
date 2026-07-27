import {test, expect, describe} from "vitest"
import * as VueFlowUtils from "../../../src/utils/vueFlowUtils.ts"

const graph = {
    nodes: [
        {uid: "1", type: "task"},
        {
            uid: "2", type: "task", task: {
                id: "task1",
                type: "io.kestra.one",
                outputFiles: ["file1.txt", "file2.txt"],
            },
        },
        {
            uid: "3", type: "task", task: {
                id: "task2",
                type: "io.kestra.two",
                outputFiles: ["file3.txt", "file4.txt"],
                taskRunner: {
                    type: "docker",
                    image: "kestra/task-runner:latest",
                },
            },
        },
        {
            uid: "4", type: "task", task: {
                id: "task3",
                type: "io.kestra.three",
            },
        },
        {
            uid: "5", type: "task", task: {
                id: "task4",
                type: "io.kestra.four",
            },
        },
        {
            uid: "6", type: "task", task: {
                id: "task5",
                type: "io.kestra.five",
            },
        },
    ],
    edges: [
        {source: "1", target: "2", id: "e1", type: "default"},
        {source: "1", target: "3", id: "e2", type: "default"},
        {source: "2", target: "4", id: "e3", type: "default"},
        {source: "3", target: "5", id: "e4", type: "default"},
        {source: "4", target: "6", id: "e5", type: "default"},
        {source: "5", target: "6", id: "e6", type: "default"},
    ],
    clusters: [],
} as any

describe("VueFlowUtils", () => {
    test("getRootNodes should return nodes with no incoming edges", () => {
        const rootNodes = VueFlowUtils.getRootNodes(graph)
        expect(rootNodes).toEqual([{uid: "1", type: "task"}])
    })

    test("getTargetNodesEdges should return edges connected to a node", () => {
        const edges = VueFlowUtils.getTargetNodesEdges(graph, "1")
        expect(edges).toEqual([
            {source: "1", target: "2", id: "e1", type: "default"},
            {source: "1", target: "3", id: "e2", type: "default"},
        ])
    })

    test("getNextTaskNodes should return next task nodes", () => {
        const nextTaskNodes = VueFlowUtils.getNextTaskNodes(graph, {uid: "1"} as any)
        expect(nextTaskNodes).toEqual([
            {
                uid: "2",
                type: "task",
                task: {
                    id: "task1",
                    type: "io.kestra.one",
                    outputFiles: [
                        "file1.txt",
                        "file2.txt",
                    ],
                },
            },
            {
                uid: "3",
                type: "task",
                task: {
                    id: "task2",
                    type: "io.kestra.two",
                    outputFiles: ["file3.txt", "file4.txt"],
                    taskRunner: {
                        type: "docker",
                        image: "kestra/task-runner:latest",
                    },
                },
            },
        ])
    })

    test("areTasksIdenticalInGraphUntilTask should return true for identical tasks", () => {
        const previousGraph = structuredClone(graph)
        const currentGraph = structuredClone(graph)
        expect(previousGraph).toEqual(currentGraph)
        expect(VueFlowUtils.areTasksIdenticalInGraphUntilTask(previousGraph, currentGraph, "task4")).toBeTruthy()
    })

    test("areTasksIdenticalInGraphUntilTask should return false for different tasks", () => {
        const previousGraph = structuredClone(graph)
        const currentGraph = structuredClone(graph)
        currentGraph.nodes[2].task.id = "task1-modified"
        expect(VueFlowUtils.areTasksIdenticalInGraphUntilTask(previousGraph, currentGraph, "task4")).toBeFalsy()
    })

    test("areTasksIdenticalInGraphUntilTask should return false for different tasks", () => {
        const previousGraph = structuredClone(graph)
        const currentGraph = structuredClone(graph)
        currentGraph.nodes[1].task.outputFiles = ["file1-modified.txt", "file4.txt"]
        expect(VueFlowUtils.areTasksIdenticalInGraphUntilTask(previousGraph, currentGraph, "task4")).toBeFalsy()
    })
})

describe("generateGraph CHOICE edge labels", () => {
    const generate = (flowGraph: any) =>
        VueFlowUtils.generateGraph(
            "vfid",
            "flow",
            "ns",
            flowGraph,
            undefined,
            [],
            false,
            {},
            new Set(),
            [],
            true,
            false,
            false,
        ) ?? []

    const issueFlowGraph = {
        nodes: [
            {
                uid: "root.render-language",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "render-language", type: "io.kestra.plugin.core.flow.Switch"},
            },
            {
                uid: "root.render-language.french",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "french", type: "io.kestra.plugin.core.debug.Return"},
            },
            {
                uid: "root.render-language.german",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "german", type: "io.kestra.plugin.core.debug.Return"},
            },
            {
                uid: "root.render-language.english",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "english", type: "io.kestra.plugin.core.debug.Return"},
            },
        ],
        edges: [
            {
                source: "root.render-language",
                target: "root.render-language.french",
                relation: {relationType: "CHOICE", value: "French"},
            },
            {
                source: "root.render-language",
                target: "root.render-language.german",
                relation: {relationType: "CHOICE", value: "German"},
            },
            {
                source: "root.render-language",
                target: "root.render-language.english",
                relation: {relationType: "CHOICE", value: "defaults"},
            },
        ],
        clusters: [],
    } as any

    test("propagates each case key from the issue flow to its CHOICE edge", () => {
        const edges = generate(issueFlowGraph).filter((e: any) => e.type === "edge")

        const frenchEdge = edges.find((e: any) => e.target === "root.render-language.french") as any
        expect(frenchEdge?.data?.value).toBe("French")
        expect(frenchEdge?.data?.relationType).toBe("CHOICE")

        const germanEdge = edges.find((e: any) => e.target === "root.render-language.german") as any
        expect(germanEdge?.data?.value).toBe("German")
        expect(germanEdge?.data?.relationType).toBe("CHOICE")

        const englishEdge = edges.find((e: any) => e.target === "root.render-language.english") as any
        expect(englishEdge?.data?.value).toBe("defaults")
        expect(englishEdge?.data?.relationType).toBe("CHOICE")
    })

    test("does not set a case value on non-CHOICE edges", () => {
        const sequentialFlowGraph = {
            nodes: [
                {
                    uid: "root.task1",
                    type: "io.kestra.core.models.hierarchies.GraphTask",
                    task: {id: "task1", type: "io.kestra.plugin.core.debug.Return"},
                },
                {
                    uid: "root.task2",
                    type: "io.kestra.core.models.hierarchies.GraphTask",
                    task: {id: "task2", type: "io.kestra.plugin.core.debug.Return"},
                },
            ],
            edges: [
                {
                    source: "root.task1",
                    target: "root.task2",
                    relation: {relationType: "SEQUENTIAL"},
                },
            ],
            clusters: [],
        } as any

        const edge = generate(sequentialFlowGraph).filter((e: any) => e.type === "edge")[0] as any
        expect(edge?.data?.value).toBeUndefined()
        expect(edge?.data?.relationType).toBe("SEQUENTIAL")
    })

    test("leaves edge data fields undefined when relation is missing", () => {
        const minimalFlowGraph = {
            nodes: [
                {
                    uid: "root.a",
                    type: "io.kestra.core.models.hierarchies.GraphTask",
                    task: {id: "a", type: "io.kestra.plugin.core.debug.Return"},
                },
                {
                    uid: "root.b",
                    type: "io.kestra.core.models.hierarchies.GraphTask",
                    task: {id: "b", type: "io.kestra.plugin.core.debug.Return"},
                },
            ],
            edges: [{source: "root.a", target: "root.b"}],
            clusters: [],
        } as any

        const edge = generate(minimalFlowGraph).filter((e: any) => e.type === "edge")[0] as any
        expect(edge?.data?.value).toBeUndefined()
        expect(edge?.data?.relationType).toBeUndefined()
    })
})