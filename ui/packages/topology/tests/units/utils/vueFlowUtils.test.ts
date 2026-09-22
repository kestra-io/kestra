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

    test("flowHaveTasks should return false for an empty source", () => {
        expect(VueFlowUtils.flowHaveTasks("")).toBeFalsy()
    })

    test("flowHaveTasks should return false when there is no root-level tasks key", () => {
        const source = `
id: flow
namespace: io.kestra.tests
`
        expect(VueFlowUtils.flowHaveTasks(source)).toBeFalsy()
    })

    test("flowHaveTasks should return false when the tasks key is not flush at the root", () => {
        // Matches the LowCodeEditor storybook fixture: an indented `tasks:` is not a root-level key.
        const source = `
id: flow
namespace: io.kestra.tests
  tasks:
    - id: task1
      type: io.kestra.plugin.core.log.Log
`
        expect(VueFlowUtils.flowHaveTasks(source)).toBeFalsy()
    })

    test("flowHaveTasks should return false when the tasks block has no task item", () => {
        const source = `
id: flow
namespace: io.kestra.tests
tasks:
description: no tasks here
`
        expect(VueFlowUtils.flowHaveTasks(source)).toBeFalsy()
    })

    test("flowHaveTasks should return false when a task item has no id line", () => {
        const source = `
id: flow
namespace: io.kestra.tests
tasks:
  - type: io.kestra.plugin.core.log.Log
`
        expect(VueFlowUtils.flowHaveTasks(source)).toBeFalsy()
    })

    test("flowHaveTasks should return true when a task item has an id line", () => {
        const source = `
id: flow
namespace: io.kestra.tests
tasks:
  - id: task1
    type: io.kestra.plugin.core.log.Log
`
        expect(VueFlowUtils.flowHaveTasks(source)).toBeTruthy()
    })

    test("flowHaveTasks should return true when the id line uses a space before the colon", () => {
        const source = `
id: flow
namespace: io.kestra.tests
tasks:
  - id : task1
    type: io.kestra.plugin.core.log.Log
`
        expect(VueFlowUtils.flowHaveTasks(source)).toBeTruthy()
    })

    test("flowHaveTasks should stop at the next root-level key and return true when found before it", () => {
        const source = `
id: flow
namespace: io.kestra.tests
tasks:
  - id: task1
    type: io.kestra.plugin.core.log.Log
triggers:
  - id: schedule
    type: io.kestra.plugin.core.trigger.Schedule
`
        expect(VueFlowUtils.flowHaveTasks(source)).toBeTruthy()
    })

    test("flowHaveTasks should not hang or blow up on a large adversarial source", () => {
        const source = "tasks:\n" + "  no-dash-line-with-lots-of-content-to-scan-through\n".repeat(50000)
        const start = performance.now()
        expect(VueFlowUtils.flowHaveTasks(source)).toBeFalsy()
        expect(performance.now() - start).toBeLessThan(1000)
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

    test("handles nested cluster collapse positioning and visibility", () => {
        const nestedClusterFlowGraph = {
            nodes: [
                {uid: "outer_loop.start", type: "io.kestra.core.models.hierarchies.GraphClusterRoot"},
                {uid: "inner_loop.start", type: "io.kestra.core.models.hierarchies.GraphClusterRoot"},
                {uid: "inner_task", type: "io.kestra.core.models.hierarchies.GraphTask", task: {id: "inner_task", type: "io.kestra.plugin.core.debug.Return"}},
                {uid: "inner_loop.end", type: "io.kestra.core.models.hierarchies.GraphClusterEnd"},
                {uid: "outer_loop.end", type: "io.kestra.core.models.hierarchies.GraphClusterEnd"},
            ],
            edges: [
                {source: "outer_loop.start", target: "inner_loop.start"},
                {source: "inner_loop.start", target: "inner_task"},
                {source: "inner_task", target: "inner_loop.end"},
                {source: "inner_loop.end", target: "outer_loop.end"},
            ],
            clusters: [
                {
                    cluster: {
                        uid: "Cluster.outer_loop",
                        type: "io.kestra.core.models.hierarchies.GraphCluster",
                        taskNode: {uid: "outer_loop", task: {id: "outer_loop", type: "io.kestra.plugin.core.flow.Loop"}},
                    },
                    nodes: ["outer_loop.start", "inner_loop.start", "inner_task", "inner_loop.end", "outer_loop.end"],
                    parents: [],
                    start: "outer_loop.start",
                    end: "outer_loop.end",
                },
                {
                    cluster: {
                        uid: "Cluster.inner_loop",
                        type: "io.kestra.core.models.hierarchies.GraphCluster",
                        taskNode: {uid: "inner_loop", task: {id: "inner_loop", type: "io.kestra.plugin.core.flow.Loop"}},
                    },
                    nodes: ["inner_loop.start", "inner_task", "inner_loop.end"],
                    parents: ["Cluster.outer_loop"],
                    start: "inner_loop.start",
                    end: "inner_loop.end",
                },
            ],
        } as any

        const collapsedInner = new Set<string>(["inner_loop"])
        const hiddenInner = ["inner_loop.start", "inner_task", "inner_loop.end", "Cluster.inner_loop"]
        const edgeReplacerInner = {
            "Cluster.inner_loop": "inner_loop",
            "inner_loop.start": "inner_loop",
            "inner_loop.end": "inner_loop",
        }
        const clusterToNodeInner: any[] = []

        const elementsInner = VueFlowUtils.generateGraph(
            "test_flow",
            "flow_id",
            "namespace",
            nestedClusterFlowGraph,
            "",
            hiddenInner,
            true,
            edgeReplacerInner,
            collapsedInner,
            clusterToNodeInner,
        )

        const innerCollapsedNode = elementsInner.find((e: any) => e.id === "inner_loop")
        expect(innerCollapsedNode).toBeDefined()
        expect(innerCollapsedNode.parentNode).toBe("Cluster.outer_loop")

        const collapsedBoth = new Set<string>(["inner_loop", "outer_loop"])
        const hiddenBoth = ["outer_loop.start", "inner_loop.start", "inner_task", "inner_loop.end", "outer_loop.end", "Cluster.outer_loop", "inner_loop", "Cluster.inner_loop"]
        const edgeReplacerBoth = {
            "Cluster.outer_loop": "outer_loop",
            "outer_loop.start": "outer_loop",
            "outer_loop.end": "outer_loop",
        }
        const clusterToNodeBoth: any[] = []

        const elementsBoth = VueFlowUtils.generateGraph(
            "test_flow",
            "flow_id",
            "namespace",
            nestedClusterFlowGraph,
            "",
            hiddenBoth,
            true,
            edgeReplacerBoth,
            collapsedBoth,
            clusterToNodeBoth,
        )

        const innerNodeWhenOuterCollapsed = elementsBoth.find((e: any) => e.id === "inner_loop")
        expect(innerNodeWhenOuterCollapsed).toBeUndefined()
    })
})