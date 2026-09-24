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
})
/** What the assertions below read off a generated element; the generator's own return type is a
 *  vue-flow union that would need narrowing at every access. */
interface GeneratedElement {
    id?: string
    type?: string
    class?: string
    draggable?: boolean
    source?: string
    parentNode?: string
    position?: {x: number; y: number}
    data?: Record<string, unknown>
}

const asElements = (elements: unknown): GeneratedElement[] => elements as GeneratedElement[]

describe("generateGraph node draggability", () => {
    const flowGraphWithCluster = {
        nodes: [
            {
                uid: "root.branch",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "branch", type: "io.kestra.plugin.core.flow.Sequential", namespace: "ns", flowId: "flow"},
            },
            {
                uid: "root.branch.child",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "child", type: "io.kestra.plugin.core.log.Log", namespace: "ns", flowId: "flow"},
            },
        ],
        edges: [
            {
                source: "root.branch",
                target: "root.branch.child",
                relation: {relationType: "SEQUENTIAL"},
            },
        ],
        clusters: [
            {
                cluster: {
                    uid: "cluster_root.branch",
                    type: "io.kestra.core.models.hierarchies.GraphCluster",
                    taskNode: {
                        uid: "root.branch",
                        task: {id: "branch", type: "io.kestra.plugin.core.flow.Sequential", namespace: "ns", flowId: "flow"},
                    },
                },
                nodes: ["root.branch.child"],
                parents: [],
            },
        ],
    } as unknown as VueFlowUtils.FlowGraph

    test("marks a task movable but never the cluster wrapping it", () => {
        const elements =
            VueFlowUtils.generateGraph(
                "vfid",
                "flow",
                "ns",
                flowGraphWithCluster,
                undefined,
                [],
                false,
                {},
                new Set(),
                [],
                false,
                true,
                false,
            ) ?? []

        // The drag is the browser's own, so vue-flow must never reposition a node itself.
        const built = asElements(elements)
        expect(built.every((element) => element.draggable !== true)).toBe(true)

        const cluster = built.find((element) => element.type === "cluster")
        expect(cluster).toBeDefined()
        expect(cluster?.data?.isMovable).toBeFalsy()

        const child = built.find((element) => element.id === "root.branch.child")
        expect(child?.data?.isMovable).toBe(true)
        // Without `nopan` a drag starting on a card pans the canvas instead.
        expect(child?.class).toContain("nopan")
    })

    const triggersGraph = {
        nodes: [
            {
                uid: "root.only_task",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "only_task", type: "io.kestra.plugin.core.log.Log", namespace: "ns", flowId: "flow"},
            },
            {
                uid: "root.branch",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "branch", type: "io.kestra.plugin.core.flow.Sequential", namespace: "ns", flowId: "flow"},
            },
        ],
        edges: [],
        clusters: [
            {
                cluster: {
                    uid: "cluster_root.Triggers",
                    type: "io.kestra.core.models.hierarchies.GraphCluster",
                },
                nodes: [],
                parents: [],
            },
            {
                cluster: {
                    uid: "cluster_root.branch",
                    type: "io.kestra.core.models.hierarchies.GraphCluster",
                    taskNode: {
                        uid: "root.branch",
                        task: {id: "branch", type: "io.kestra.plugin.core.flow.Sequential", namespace: "ns", flowId: "flow"},
                    },
                },
                nodes: [],
                parents: [],
            },
        ],
    } as unknown as VueFlowUtils.FlowGraph

    const clustersOf = (isReadOnly: boolean, isAllowedEdit: boolean) =>
        (VueFlowUtils.generateGraph(
            "vfid",
            "flow",
            "ns",
            triggersGraph,
            undefined,
            [],
            false,
            {},
            new Set(),
            [],
            isReadOnly,
            isAllowedEdit,
            false,
        ) ?? []).filter((element) => asElements([element])[0].type === "cluster")

    test("offers the add-trigger button on the triggers box only, and only when editing", () => {
        const editable = clustersOf(false, true)
        const triggers = editable.find((c) => c.id === "cluster_root.Triggers")
        const flowable = editable.find((c) => c.id === "cluster_root.branch")
        expect(triggers?.data?.canAddTrigger).toBe(true)
        expect(flowable?.data?.canAddTrigger).toBe(false)

        // A trigger is still a change to the flow, so read-only and view-only must not offer it.
        for (const [readOnly, allowedEdit] of [[true, true], [false, false]] as const) {
            const guarded = clustersOf(readOnly, allowedEdit)
            const box = guarded.find((c) => c.id === "cluster_root.Triggers")
            expect(box?.data?.canAddTrigger, `readOnly=${readOnly} allowedEdit=${allowedEdit}`).toBe(false)
        }
    })

    // The flow is no longer a node: it is a canvas-anchored chip. Asserting that every element
    // traces back to a uid the backend sent catches any synthetic node appended to the graph,
    // whatever it ends up being called.
    test("emits only the nodes and clusters the backend graph declares", () => {
        const declaredUids = new Set([
            ...triggersGraph.nodes.map((node) => node.uid),
            ...triggersGraph.clusters!.map((entry) => entry.cluster.uid),
        ])

        const elements = VueFlowUtils.generateGraph(
            "vfid", "flow", "ns", triggersGraph, undefined, [], false, {}, new Set(), [], false, true, false,
        ) ?? []

        expect(elements.length).toBe(declaredUids.size)
        for (const element of asElements(elements)) {
            expect(declaredUids, `element ${element.id}`).toContain(String(element.id))
            if (element.source !== undefined) {
                expect(declaredUids, `edge source ${element.source}`).toContain(String(element.source))
            }
        }
    })
})

describe("generateGraph flowable lane header", () => {
    // A root-level Parallel with 3 branches — mirrors the real shape GraphUtils sends: the
    // flowable's own gate node (`root.parallel_task`) is both a plain node in `nodes` and its own
    // cluster's `taskNode`, and the cluster's `nodes` list carries root/end plus every child.
    const parallelFlowGraph = {
        nodes: [
            {uid: "root.root-1", type: "io.kestra.core.models.hierarchies.GraphClusterRoot"},
            {uid: "root.end-1", type: "io.kestra.core.models.hierarchies.GraphClusterEnd"},
            {
                uid: "root.parallel_task",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "parallel_task", type: "io.kestra.plugin.core.flow.Parallel", namespace: "ns", flowId: "flow"},
            },
            {
                uid: "root.parallel_task.branch_a",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "branch_a", type: "io.kestra.plugin.core.log.Log", namespace: "ns", flowId: "flow"},
            },
            {
                uid: "root.parallel_task.branch_b",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "branch_b", type: "io.kestra.plugin.core.log.Log", namespace: "ns", flowId: "flow"},
            },
        ],
        edges: [
            {source: "root.parallel_task", target: "root.parallel_task.branch_a", relation: {relationType: "PARALLEL"}},
            {source: "root.parallel_task", target: "root.parallel_task.branch_b", relation: {relationType: "PARALLEL"}},
        ],
        clusters: [
            {
                cluster: {
                    uid: "cluster_root.parallel_task",
                    type: "io.kestra.core.models.hierarchies.GraphCluster",
                    taskNode: {
                        uid: "root.parallel_task",
                        task: {id: "parallel_task", type: "io.kestra.plugin.core.flow.Parallel", namespace: "ns", flowId: "flow"},
                    },
                },
                nodes: ["root.root-1", "root.end-1", "root.parallel_task", "root.parallel_task.branch_a", "root.parallel_task.branch_b"],
                parents: [],
                start: "root.root-1",
                end: "root.end-1",
            },
        ],
    } as unknown as VueFlowUtils.FlowGraph

    const generate = () =>
        asElements(VueFlowUtils.generateGraph(
            "vfid", "flow", "ns", parallelFlowGraph, undefined, [], false, {}, new Set(), [], true, false, false,
        ) ?? [])

    test("renders the flowable's own gate node as an invisible connector, not a second task box", () => {
        const gate = generate().find((e) => e.id === "root.parallel_task")

        expect(gate?.type).toBe("dot")
    })

    test("gives the cluster its lane-header data: type, id and child count come from one place", () => {
        const cluster = generate().find((e) => e.id === "cluster_root.parallel_task")

        expect(cluster?.data?.isFlowableLane).toBe(true)
        expect(cluster?.data?.taskNode).toMatchObject({uid: "root.parallel_task"})
        expect(cluster?.data?.childTaskIds).toEqual(["branch_a", "branch_b"])
    })

    test("does not lay the gate node out at the task footprint — dagre sees a dot, not a box", () => {
        const gate = generate().find((e) => e.id === "root.parallel_task")

        expect(gate?.style).toMatchObject({width: "5px", height: "5px"})
    })

    // Regression: the collapsed placeholder shares the flowable's own uid, so the dot-sizing
    // override for a hidden gate node must not also catch it and shrink it to a 5x5 dot.
    test("still sizes a collapsed lane as a collapsed cluster, not as a hidden gate dot", () => {
        // Mirrors Topology.vue's collapseCluster(): the cluster's own children plus its uid go
        // into hiddenNodes so the ordinary node entry is suppressed and only the placeholder shows.
        const hiddenNodes = [
            "root.root-1", "root.end-1", "root.parallel_task", "root.parallel_task.branch_a", "root.parallel_task.branch_b",
            "cluster_root.parallel_task",
        ]
        const edgeReplacer = {
            "cluster_root.parallel_task": "root.parallel_task",
            "root.root-1": "root.parallel_task",
            "root.end-1": "root.parallel_task",
        }
        const elements = asElements(VueFlowUtils.generateGraph(
            "vfid", "flow", "ns", parallelFlowGraph, undefined, hiddenNodes, false, edgeReplacer, new Set(["root.parallel_task"]), [], true, false, false,
        ) ?? [])

        const collapsed = elements.find((e) => e.id === "root.parallel_task")
        expect(collapsed?.type).toBe("collapsedcluster")
        expect(collapsed?.style).toMatchObject({width: "150px", height: "40px"})
    })
})

describe("generateGraph synthetic errors lane", () => {
    const flowWithRootErrors = {
        nodes: [
            {
                uid: "root.log_start",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "log_start", type: "io.kestra.plugin.core.log.Log", namespace: "ns", flowId: "flow"},
            },
            {
                uid: "root.error_handler",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                branchType: "ERROR",
                task: {id: "error_handler", type: "io.kestra.plugin.core.log.Log", namespace: "ns", flowId: "flow"},
            },
        ],
        edges: [
            {source: "root.log_start", target: "root.error_handler", relation: {relationType: "ERROR"}},
        ],
        clusters: [],
    } as unknown as VueFlowUtils.FlowGraph

    test("wraps a flow-level errors: task in its own labelled lane instead of leaving it floating", () => {
        const elements = asElements(VueFlowUtils.generateGraph(
            "vfid", "flow", "ns", flowWithRootErrors, undefined, [], false, {}, new Set(), [], true, false, false,
        ) ?? [])

        const errorsLane = elements.find((e) => e.type === "cluster" && e.id === "cluster_root.Errors")
        expect(errorsLane).toBeDefined()
        expect(errorsLane?.class).toBe("ks-topology-errors-border")

        const errorTask = elements.find((e) => e.id === "root.error_handler")
        expect(errorTask?.parentNode).toBe("cluster_root.Errors")
    })

    test("does not synthesize an errors lane when there is nothing to wrap", () => {
        const flowGraph = {
            nodes: [
                {
                    uid: "root.log_start",
                    type: "io.kestra.core.models.hierarchies.GraphTask",
                    task: {id: "log_start", type: "io.kestra.plugin.core.log.Log", namespace: "ns", flowId: "flow"},
                },
            ],
            edges: [],
            clusters: [],
        } as unknown as VueFlowUtils.FlowGraph

        const elements = asElements(VueFlowUtils.generateGraph(
            "vfid", "flow", "ns", flowGraph, undefined, [], false, {}, new Set(), [], true, false, false,
        ) ?? [])

        expect(elements.some((e) => e.type === "cluster")).toBe(false)
    })
})

describe("buildEffectiveGetNodeDimensions (footprint invariance)", () => {
    const taskNode = {uid: "root.a", type: "io.kestra.core.models.hierarchies.GraphTask"}

    test("returns the constant task footprint with no execution", () => {
        const getDimensions = VueFlowUtils.buildEffectiveGetNodeDimensions(false)
        const dimensions = getDimensions(taskNode, VueFlowUtils.getNodeWidth, VueFlowUtils.getNodeHeight)

        expect(dimensions).toEqual({width: 218, height: 80})
    })

    // The only thing this function ever varies by is whether an execution is loaded — never a
    // zoom level, since it has no zoom parameter to read one from in the first place.
    test("varies only the width when an execution is loaded, never the height", () => {
        const withoutExecution = VueFlowUtils.buildEffectiveGetNodeDimensions(false)(taskNode, VueFlowUtils.getNodeWidth, VueFlowUtils.getNodeHeight)
        const withExecution = VueFlowUtils.buildEffectiveGetNodeDimensions(true)(taskNode, VueFlowUtils.getNodeWidth, VueFlowUtils.getNodeHeight)

        expect(withExecution.height).toBe(withoutExecution.height)
        expect(withExecution.width).toBe(273)
    })

    test("is deterministic: calling it repeatedly for the same node never drifts", () => {
        const getDimensions = VueFlowUtils.buildEffectiveGetNodeDimensions(false)
        const results = Array.from({length: 5}, () => getDimensions(taskNode, VueFlowUtils.getNodeWidth, VueFlowUtils.getNodeHeight))

        expect(new Set(results.map((r) => JSON.stringify(r))).size).toBe(1)
    })
})
