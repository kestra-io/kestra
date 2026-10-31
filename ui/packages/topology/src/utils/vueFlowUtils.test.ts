import {test, expect, describe} from "vitest"
import {useVueFlow} from "@vue-flow/core"
import * as VueFlowUtils from "./vueFlowUtils.ts"
import {
    edgeTurnPosition,
    fanOutSplitPosition,
    nodeColor,
    computeClusterColor,
    getEdgeColorToken,
    getEdgeColor,
    BranchType,
    type Cluster,
    type MinimalNode,
} from "./vueFlowUtils.ts"
import {CLUSTER_PREFIX, DAGRE_RANK_SEP, NODE_SIZES} from "./constants.ts"

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
}

describe("graph node type predicates", () => {
    test("isTaskNode matches GraphTask and SubflowGraphTask$1 suffixes only", () => {
        expect(VueFlowUtils.isTaskNode({uid: "1", type: "io.kestra.GraphTask"})).toBe(true)
        expect(VueFlowUtils.isTaskNode({uid: "2", type: "io.kestra.SubflowGraphTask$1"})).toBe(true)
        expect(VueFlowUtils.isTaskNode({uid: "6", type: "GraphTask"})).toBe(true)
        expect(VueFlowUtils.isTaskNode({uid: "7", type: "SubflowGraphTask$1"})).toBe(true)
        expect(VueFlowUtils.isTaskNode({uid: "3", type: "io.kestra.GraphTaskExtra"})).toBe(false)
        expect(VueFlowUtils.isTaskNode({uid: "4", type: "GraphTask.io.kestra"})).toBe(false)
        expect(VueFlowUtils.isTaskNode({uid: "5", type: "io.kestra.Other"})).toBe(false)
        expect(VueFlowUtils.isTaskNode({uid: "8", type: "io.kestra.graphtask"})).toBe(false)
    })

    test("isTriggerNode matches only the GraphTrigger suffix", () => {
        expect(VueFlowUtils.isTriggerNode({uid: "1", type: "io.kestra.GraphTrigger"})).toBe(true)
        expect(VueFlowUtils.isTriggerNode({uid: "4", type: "GraphTrigger"})).toBe(true)
        expect(VueFlowUtils.isTriggerNode({uid: "2", type: "GraphTriggerExtra"})).toBe(false)
        expect(VueFlowUtils.isTriggerNode({uid: "3", type: "GraphTrigger.io.kestra"})).toBe(false)
        expect(VueFlowUtils.isTriggerNode({uid: "5", type: "io.kestra.graphtrigger"})).toBe(false)
    })

    test("isCustomNode matches only the CustomGraphNode suffix", () => {
        expect(VueFlowUtils.isCustomNode({uid: "1", type: "io.kestra.CustomGraphNode"})).toBe(true)
        expect(VueFlowUtils.isCustomNode({uid: "4", type: "CustomGraphNode"})).toBe(true)
        expect(VueFlowUtils.isCustomNode({uid: "2", type: "CustomGraphNodeExtra"})).toBe(false)
        expect(VueFlowUtils.isCustomNode({uid: "3", type: "CustomGraphNode.io.kestra"})).toBe(false)
        expect(VueFlowUtils.isCustomNode({uid: "5", type: "io.kestra.customgraphnode"})).toBe(false)
    })

    test("isCollapsedCluster identifies collapsed clusters", () => {
        expect(VueFlowUtils.isCollapsedCluster({uid: "1", type: "collapsedcluster"})).toBe(true)
        expect(VueFlowUtils.isCollapsedCluster({uid: "2", type: "GraphCluster"})).toBe(false)
    })

    test("isCollapsedLane requires a collapsed cluster and a flowable lane", () => {
        expect(VueFlowUtils.isCollapsedLane({
            uid: "1",
            type: "collapsedcluster",
            isFlowableLane: true,
        })).toBe(true)

        expect(VueFlowUtils.isCollapsedLane({
            uid: "2",
            type: "collapsedcluster",
            isFlowableLane: false,
        })).toBe(false)

        expect(VueFlowUtils.isCollapsedLane({
            uid: "3",
            type: "GraphCluster",
            isFlowableLane: true,
        })).toBe(false)

        expect(VueFlowUtils.isCollapsedLane({
            uid: "4",
            type: "collapsedcluster",
        })).toBe(false)
    })

    test("isExpandableTask identifies a flowable task and rejects a leaf task", () => {
        const flowableTask = {
            uid: "root.parallel",
            type: "io.kestra.GraphTask",
            task: {type: "io.kestra.plugin.core.flow.Parallel"},
        }
        const edgeReplacer = {"cluster_root.parallel": "root.parallel"}

        expect(VueFlowUtils.isExpandableTask(flowableTask, {}, edgeReplacer)).toBe(true)
        expect(VueFlowUtils.isExpandableTask({
            uid: "root.log",
            type: "io.kestra.GraphTask",
            task: {type: "io.kestra.plugin.core.log.Log"},
        }, {}, {})).toBe(false)
    })

    test("isExpandableTask enables nested subflow interaction when configured", () => {
        const node = {uid: "root.subflow", type: "io.kestra.SubflowGraphTask"}
        const clusterByNodeUid: Record<string, Cluster> = {
            "root.subflow": {
                uid: "cluster_root.parent",
                type: "io.kestra.GraphCluster",
                taskNode: null,
            },
        }

        expect(VueFlowUtils.isExpandableTask(node, clusterByNodeUid, {}, true)).toBe(true)
        expect(VueFlowUtils.isExpandableTask(node, clusterByNodeUid, {}, false)).toBe(false)
        expect(VueFlowUtils.isExpandableTask(
            {...node, type: "SubflowGraphTask"},
            clusterByNodeUid,
            {},
            true,
        )).toBe(true)
        expect(VueFlowUtils.isExpandableTask(
            {...node, type: "io.kestra.SubflowGraphTaskExtra"},
            clusterByNodeUid,
            {},
            true,
        )).toBe(false)
        expect(VueFlowUtils.isExpandableTask(
            {uid: "root.lowercase", type: "io.kestra.subflowgraphtask"},
            clusterByNodeUid,
            {},
            true,
        )).toBe(false)
    })

    test("isClusterRootOrEnd identifies synthetic cluster boundary nodes", () => {
        for (const suffix of [
            "GraphClusterRoot",
            "GraphClusterFinally",
            "GraphClusterAfterExecution",
            "GraphClusterEnd",
        ]) {
            expect(VueFlowUtils.isClusterRootOrEnd({
                uid: suffix,
                type: `io.kestra.${suffix}`,
            })).toBe(true)
            expect(VueFlowUtils.isClusterRootOrEnd({
                uid: suffix,
                type: suffix,
            })).toBe(true)
        }

        expect(VueFlowUtils.isClusterRootOrEnd({
            uid: "other",
            type: "io.kestra.GraphClusterRootExtra",
        })).toBe(false)

        expect(VueFlowUtils.isClusterRootOrEnd({
            uid: "other",
            type: "GraphClusterEnd.io.kestra",
        })).toBe(false)
        expect(VueFlowUtils.isClusterRootOrEnd({
            uid: "other",
            type: "io.kestra.graphclusterroot",
        })).toBe(false)
    })

    test("isTrueFlowableCluster requires a task node and excludes subflow and triggers clusters", () => {
        const flowableCluster: Cluster = {
            uid: "cluster_root.parallel",
            type: "io.kestra.plugin.core.flow.Parallel",
            taskNode: {
                uid: "root.parallel",
                task: {type: "io.kestra.plugin.core.flow.Parallel"},
            },
        }

        expect(VueFlowUtils.isTrueFlowableCluster(flowableCluster)).toBe(true)

        expect(VueFlowUtils.isTrueFlowableCluster({
            ...flowableCluster,
            taskNode: null,
        })).toBe(false)

        expect(VueFlowUtils.isTrueFlowableCluster({
            ...flowableCluster,
            type: "io.kestra.plugin.core.flow.SubflowGraphCluster",
        })).toBe(false)

        expect(VueFlowUtils.isTrueFlowableCluster({
            ...flowableCluster,
            type: "SubflowGraphCluster",
        })).toBe(false)

        expect(VueFlowUtils.isTrueFlowableCluster({
            ...flowableCluster,
            type: "io.kestra.plugin.core.flow.SubflowGraphClusterExtra",
        })).toBe(true)

        expect(VueFlowUtils.isTrueFlowableCluster({
            ...flowableCluster,
            uid: "cluster_root.Triggers",
        })).toBe(false)
    })
})

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
        const nextTaskNodes = VueFlowUtils.getNextTaskNodes(graph, {uid: "1", type: "task"})
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
        const task: Record<string, unknown> | undefined = currentGraph.nodes[2].task
        if (task) task.id = "task1-modified"
        expect(VueFlowUtils.areTasksIdenticalInGraphUntilTask(previousGraph, currentGraph, "task4")).toBeFalsy()
    })

    test("areTasksIdenticalInGraphUntilTask should return false for different tasks", () => {
        const previousGraph = structuredClone(graph)
        const currentGraph = structuredClone(graph)
        const task: Record<string, unknown> | undefined = currentGraph.nodes[1].task
        if (task) task.outputFiles = ["file1-modified.txt", "file4.txt"]
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
    const generate = (flowGraph: VueFlowUtils.FlowGraph) =>
        asElements(VueFlowUtils.generateGraph(
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
        ) ?? [])

    const issueFlowGraph: VueFlowUtils.FlowGraph = {
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
    }

    test("propagates each case key from the issue flow to its CHOICE edge", () => {
        const edges = generate(issueFlowGraph).filter((e) => e.type === "edge")

        const frenchEdge = edges.find((e) => e.target === "root.render-language.french")
        expect(frenchEdge?.data?.value).toBe("French")
        expect(frenchEdge?.data?.relationType).toBe("CHOICE")

        const germanEdge = edges.find((e) => e.target === "root.render-language.german")
        expect(germanEdge?.data?.value).toBe("German")
        expect(germanEdge?.data?.relationType).toBe("CHOICE")

        const englishEdge = edges.find((e) => e.target === "root.render-language.english")
        expect(englishEdge?.data?.value).toBe("defaults")
        expect(englishEdge?.data?.relationType).toBe("CHOICE")
    })

    test("does not set a case value on non-CHOICE edges", () => {
        const sequentialFlowGraph: VueFlowUtils.FlowGraph = {
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
        }

        const edge = generate(sequentialFlowGraph).filter((e) => e.type === "edge")[0]
        expect(edge?.data?.value).toBeUndefined()
        expect(edge?.data?.relationType).toBe("SEQUENTIAL")
    })

    test("leaves edge data fields undefined when relation is missing", () => {
        const minimalFlowGraph: VueFlowUtils.FlowGraph = {
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
        }

        const edge = generate(minimalFlowGraph).filter((e) => e.type === "edge")[0]
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
    target?: string
    parentNode?: string
    position?: {x: number; y: number}
    style?: Record<string, string>
    markerEnd?: unknown
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

describe("generateGraph triggers lane join dot", () => {
    const TRIGGER_UID = "root.Triggers.on_infra_failure"
    const SECOND_TRIGGER_UID = "root.Triggers.nightly"
    const TRIGGERS_START = "root.Triggers.root-1"
    const TRIGGERS_END = "root.Triggers.end-1"
    const TRIGGERS_CLUSTER = CLUSTER_PREFIX + "root.Triggers"
    const FLOW_ROOT = "root.root-1"
    const FLOW_FINALLY = "root.finally-1"
    const FLOW_END = "root.end-1"

    // What the backend emits for a flow with one trigger and one task: the triggers lane closes on
    // its own end dot, which is wired straight into the flow's root dot (GraphUtils#of).
    const triggeredFlowGraph = (extra: {nodes?: unknown[]; edges?: unknown[]} = {}) => ({
        nodes: [
            {uid: FLOW_ROOT, type: "io.kestra.core.models.hierarchies.GraphClusterRoot"},
            {uid: FLOW_END, type: "io.kestra.core.models.hierarchies.GraphClusterEnd"},
            {uid: TRIGGERS_START, type: "io.kestra.core.models.hierarchies.GraphClusterRoot"},
            {uid: TRIGGER_UID, type: "io.kestra.core.models.hierarchies.GraphTrigger"},
            {uid: TRIGGERS_END, type: "io.kestra.core.models.hierarchies.GraphClusterEnd"},
            {
                uid: "root.notify_team",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "notify_team", type: "io.kestra.plugin.core.log.Log", namespace: "ns", flowId: "flow"},
            },
            ...(extra.nodes ?? []),
        ],
        edges: [
            {source: TRIGGERS_START, target: TRIGGER_UID},
            {source: TRIGGER_UID, target: TRIGGERS_END},
            {source: TRIGGERS_END, target: FLOW_ROOT},
            {source: FLOW_ROOT, target: "root.notify_team"},
            {source: "root.notify_team", target: FLOW_END},
            ...(extra.edges ?? []),
        ],
        clusters: [
            {
                cluster: {
                    uid: TRIGGERS_CLUSTER,
                    type: "io.kestra.core.models.hierarchies.GraphCluster",
                    taskNode: null,
                },
                nodes: [TRIGGERS_START, TRIGGER_UID, TRIGGERS_END],
                parents: [],
                start: TRIGGERS_START,
                end: TRIGGERS_END,
            },
        ],
    }) as unknown as VueFlowUtils.FlowGraph

    // A second trigger makes the lane end the junction both triggers converge on, exactly as the
    // backend wires it: one edge from the lane root per trigger, one from each trigger to the end.
    const withSecondTrigger = () => {
        const flowGraph = triggeredFlowGraph()
        flowGraph.nodes.splice(4, 0, {
            uid: SECOND_TRIGGER_UID,
            type: "io.kestra.core.models.hierarchies.GraphTrigger",
        })
        flowGraph.edges.splice(2, 0,
            {source: TRIGGERS_START, target: SECOND_TRIGGER_UID},
            {source: SECOND_TRIGGER_UID, target: TRIGGERS_END},
        )
        flowGraph.clusters[0].nodes.splice(2, 0, SECOND_TRIGGER_UID)
        return flowGraph
    }

    // `errors:` hangs its first task off the flow root, `finally:` hangs its own off the finally
    // marker, and both the main chain and the error chain run into that marker (GraphUtils#fillGraph).
    // The flow root therefore branches, which is what keeps it rendered.
    const withErrorAndFinally = () => {
        const flowGraph = triggeredFlowGraph({
            nodes: [
                {uid: FLOW_FINALLY, type: "io.kestra.core.models.hierarchies.GraphClusterFinally"},
                {
                    uid: "root.handle_error",
                    type: "io.kestra.core.models.hierarchies.GraphTask",
                    branchType: BranchType.ERROR,
                    task: {id: "handle_error", type: "io.kestra.plugin.core.log.Log"},
                },
                {
                    uid: "root.cleanup",
                    type: "io.kestra.core.models.hierarchies.GraphTask",
                    branchType: BranchType.FINALLY,
                    task: {id: "cleanup", type: "io.kestra.plugin.core.log.Log"},
                },
            ],
            edges: [
                {source: FLOW_ROOT, target: "root.handle_error"},
                {source: "root.handle_error", target: FLOW_FINALLY},
                {source: FLOW_FINALLY, target: "root.cleanup"},
                {source: "root.cleanup", target: FLOW_END},
            ],
        })
        // With a `finally:` section the main chain runs into the marker, not straight to the end.
        flowGraph.edges[4] = {source: "root.notify_team", target: FLOW_FINALLY}
        return flowGraph
    }

    const generateTriggered = (flowGraph: VueFlowUtils.FlowGraph) =>
        asElements(VueFlowUtils.generateGraph(
            "vfid", "flow", "ns", flowGraph, undefined, [], false, {}, new Set(), [], false, true, false,
        ) ?? [])

    const edgePairs = (elements: GeneratedElement[]) =>
        elements.filter((element) => element.type === "edge").map((edge) => `${edge.source}|${edge.target}`)

    const laneMembers = (elements: GeneratedElement[]) =>
        elements.filter((element) => element.parentNode === TRIGGERS_CLUSTER).map((element) => element.id).sort()

    test("drops the flow root dot so one joint sits between the trigger and the first task", () => {
        const elements = generateTriggered(triggeredFlowGraph())

        expect(elements.map((element) => element.id)).not.toContain(FLOW_ROOT)
        expect(edgePairs(elements)).toEqual([
            `${TRIGGERS_START}|${TRIGGER_UID}`,
            `${TRIGGER_UID}|${TRIGGERS_END}`,
            `${TRIGGERS_END}|root.notify_team`,
            `root.notify_team|${FLOW_END}`,
        ])
    })

    test("keeps the lane's own end dot, so the box keeps the member that sizes it", () => {
        const elements = generateTriggered(triggeredFlowGraph())

        const laneEnd = elements.find((element) => element.id === TRIGGERS_END)
        expect(laneEnd?.type).toBe("dot")
        expect(laneEnd?.parentNode).toBe(TRIGGERS_CLUSTER)
        expect(laneMembers(elements)).toEqual([TRIGGERS_START, TRIGGER_UID, TRIGGERS_END].sort())
    })

    test("leaves the triggers lane the same size whether or not the flow root is folded", () => {
        // `withErrorAndFinally` branches at the flow root, so no fold happens there — it is the
        // untouched baseline the folded graph's lane has to match.
        const folded = generateTriggered(triggeredFlowGraph())
        const baseline = generateTriggered(withErrorAndFinally())

        const lane = (elements: GeneratedElement[]) => elements.find((element) => element.id === TRIGGERS_CLUSTER)
        expect(lane(folded)?.style?.height).toBe(lane(baseline)?.style?.height)
        expect(lane(folded)?.style?.width).toBe(lane(baseline)?.style?.width)
        expect(laneMembers(folded)).toEqual(laneMembers(baseline))
    })

    test("drops the folded edge instead of emitting a self-edge on the lane end", () => {
        for (const flowGraph of [triggeredFlowGraph(), withSecondTrigger()]) {
            const elements = generateTriggered(flowGraph)

            expect(elements.filter((element) => element.type === "edge")
                .some((edge) => edge.source === edge.target)).toBe(false)
            expect(elements.map((element) => element.id)).not.toContain(`${TRIGGERS_END}|${TRIGGERS_END}`)
        }
    })

    test("keeps the shared fan-in and every branch edge inside the triggers lane", () => {
        const elements = generateTriggered(withSecondTrigger())

        // Both triggers and the dot they meet at stay members of the lane, so the fan-in is drawn
        // inside the green box rather than below it.
        expect(laneMembers(elements))
            .toEqual([TRIGGERS_START, TRIGGER_UID, SECOND_TRIGGER_UID, TRIGGERS_END].sort())
        expect(elements.find((element) => element.id === TRIGGERS_END)?.parentNode).toBe(TRIGGERS_CLUSTER)

        const insideLane = [
            `${TRIGGERS_START}|${TRIGGER_UID}`,
            `${TRIGGERS_START}|${SECOND_TRIGGER_UID}`,
            `${TRIGGER_UID}|${TRIGGERS_END}`,
            `${SECOND_TRIGGER_UID}|${TRIGGERS_END}`,
        ]
        expect(edgePairs(elements)).toEqual(expect.arrayContaining(insideLane))
        // One run leaves the lane, straight to the first task.
        expect(edgePairs(elements).filter((pair) => pair.startsWith(`${TRIGGERS_END}|`)))
            .toEqual([`${TRIGGERS_END}|root.notify_team`])
    })

    test("leaves no dot between the triggers box and the first task", () => {
        for (const flowGraph of [triggeredFlowGraph(), withSecondTrigger()]) {
            const elements = generateTriggered(flowGraph)
            const outside = elements
                .filter((element) => element.type === "dot" && element.parentNode === undefined)
                .map((element) => element.id)

            // Only the flow's own end marker below the task; nothing orphaned in the gap above it.
            expect(outside).toEqual([FLOW_END])
        }
    })

    test("keeps the add button on the edge into the first task", () => {
        for (const flowGraph of [triggeredFlowGraph(), withSecondTrigger()]) {
            const edges = generateTriggered(flowGraph).filter((element) => element.type === "edge")

            expect(edges.find((edge) => edge.target === "root.notify_team")?.data?.haveAdd)
                .toEqual({refId: "notify_team", position: "before"})
            // A trigger is not a task, so the lane's own wiring still offers nothing to insert.
            expect(edges.find((edge) => edge.target === TRIGGERS_END)?.data?.haveAdd).toBeFalsy()
            expect(edges.find((edge) => edge.target === TRIGGER_UID)?.data?.haveAdd).toBeFalsy()
        }
    })

    test("keeps the flow root and its branches when errors and finally fan out of it", () => {
        const elements = generateTriggered(withErrorAndFinally())

        // The root is a junction here, so it is not folded and nothing moves into the lane.
        expect(elements.map((element) => element.id)).toContain(FLOW_ROOT)
        expect(elements.find((element) => element.id === FLOW_ROOT)?.parentNode).toBeUndefined()
        expect(edgePairs(elements)).toEqual(expect.arrayContaining([
            `${TRIGGERS_END}|${FLOW_ROOT}`,
            `${FLOW_ROOT}|root.notify_team`,
            `${FLOW_ROOT}|root.handle_error`,
        ]))
    })

    // The exact uids the backend produced for system/branches_check, read off the rendered DOM
    // (kestra-io/kestra#21047), so the selector is exercised against real ids and not tidy fixtures.
    test("keeps the flow root unfolded for the real branches_check uids", () => {
        const REAL_TRIGGERS_END = "root.Triggers.end-5mRuovhdoehYXHm7tVktgo"
        const REAL_FLOW_ROOT = "root.root-YpQDXkcWb90U6n5iAEU6J"
        const realGraph = {
            nodes: [
                {uid: REAL_FLOW_ROOT, type: "io.kestra.core.models.hierarchies.GraphClusterRoot"},
                {uid: "root.finally-Zk1", type: "io.kestra.core.models.hierarchies.GraphClusterFinally"},
                {uid: "root.end-Qm2", type: "io.kestra.core.models.hierarchies.GraphClusterEnd"},
                {uid: "root.Triggers.root-Ab3", type: "io.kestra.core.models.hierarchies.GraphClusterRoot"},
                {uid: "root.Triggers.on_infra_failure", type: "io.kestra.core.models.hierarchies.GraphTrigger"},
                {uid: REAL_TRIGGERS_END, type: "io.kestra.core.models.hierarchies.GraphClusterEnd"},
                {uid: "root.notify_team", type: "io.kestra.core.models.hierarchies.GraphTask", task: {id: "notify_team", type: "io.kestra.plugin.core.log.Log"}},
                {uid: "root.handle_error", type: "io.kestra.core.models.hierarchies.GraphTask", branchType: BranchType.ERROR, task: {id: "handle_error", type: "io.kestra.plugin.core.log.Log"}},
                {uid: "root.cleanup", type: "io.kestra.core.models.hierarchies.GraphTask", branchType: BranchType.FINALLY, task: {id: "cleanup", type: "io.kestra.plugin.core.log.Log"}},
            ],
            edges: [
                {source: "root.Triggers.root-Ab3", target: "root.Triggers.on_infra_failure"},
                {source: "root.Triggers.on_infra_failure", target: REAL_TRIGGERS_END},
                {source: REAL_TRIGGERS_END, target: REAL_FLOW_ROOT},
                {source: REAL_FLOW_ROOT, target: "root.notify_team"},
                {source: REAL_FLOW_ROOT, target: "root.handle_error"},
                {source: "root.notify_team", target: "root.finally-Zk1"},
                {source: "root.handle_error", target: "root.finally-Zk1"},
                {source: "root.finally-Zk1", target: "root.cleanup"},
                {source: "root.cleanup", target: "root.end-Qm2"},
            ],
            clusters: [{
                cluster: {uid: TRIGGERS_CLUSTER, type: "io.kestra.core.models.hierarchies.GraphCluster", taskNode: null},
                nodes: ["root.Triggers.root-Ab3", "root.Triggers.on_infra_failure", REAL_TRIGGERS_END],
                parents: [], start: "root.Triggers.root-Ab3", end: REAL_TRIGGERS_END,
            }],
        } as unknown as VueFlowUtils.FlowGraph

        const elements = generateTriggered(realGraph)

        // The structural node and all three of its edges survive.
        const flowRoot = elements.find((element) => element.id === REAL_FLOW_ROOT)
        expect(flowRoot?.type).toBe("dot")
        expect(edgePairs(elements)).toEqual(expect.arrayContaining([
            `${REAL_TRIGGERS_END}|${REAL_FLOW_ROOT}`,
            `${REAL_FLOW_ROOT}|root.notify_team`,
            `${REAL_FLOW_ROOT}|root.handle_error`,
        ]))
        // The lane keeps its own end marker, so the box is unchanged.
        expect(elements.find((element) => element.id === REAL_TRIGGERS_END)?.parentNode).toBe(TRIGGERS_CLUSTER)
    })

    // The red Errors lane is synthesized by withSyntheticErrorsLane for flow-level `errors:` tasks
    // and predates this change (kestra-io/kestra#19666); the fold must leave it exactly as it was.
    test("leaves the synthetic Errors lane and its membership untouched", () => {
        const elements = generateTriggered(withErrorAndFinally())
        const errorsLane = CLUSTER_PREFIX + "root.Errors"

        expect(elements.find((element) => element.id === errorsLane)?.type).toBe("cluster")
        expect(elements.find((element) => element.id === "root.handle_error")?.parentNode).toBe(errorsLane)
    })

    test("leaves the finally marker and the chains running into it untouched", () => {
        const elements = generateTriggered(withErrorAndFinally())

        expect(elements.find((element) => element.id === FLOW_FINALLY)?.type).toBe("dot")
        expect(edgePairs(elements)).toEqual(expect.arrayContaining([
            `root.notify_team|${FLOW_FINALLY}`,
            `root.handle_error|${FLOW_FINALLY}`,
            `${FLOW_FINALLY}|root.cleanup`,
        ]))
    })

    test("folds the flow root onto the lane end for one trigger and for several", () => {
        for (const flowGraph of [triggeredFlowGraph(), withSecondTrigger()]) {
            expect(VueFlowUtils.getTriggersJoinAlias(flowGraph.clusters, flowGraph.nodes, flowGraph.edges))
                .toEqual({[FLOW_ROOT]: TRIGGERS_END})
        }
    })

    test("aliases nothing when the flow root branches", () => {
        const flowGraph = withErrorAndFinally()

        expect(VueFlowUtils.getTriggersJoinAlias(flowGraph.clusters, flowGraph.nodes, flowGraph.edges))
            .toEqual({})
    })

    test("aliases nothing when the triggers lane is collapsed", () => {
        const flowGraph = triggeredFlowGraph()

        expect(VueFlowUtils.getTriggersJoinAlias(
            flowGraph.clusters, flowGraph.nodes, flowGraph.edges, new Set(["root.Triggers"]),
        )).toEqual({})
    })

    test("aliases nothing when the triggers lane is absorbed by a collapsed parent", () => {
        const flowGraph = triggeredFlowGraph()

        expect(VueFlowUtils.getTriggersJoinAlias(
            flowGraph.clusters, flowGraph.nodes, flowGraph.edges, new Set(), {[TRIGGERS_CLUSTER]: "root.outer"},
        )).toEqual({})
    })

    test("aliases nothing when the lane end is already replaced by a placeholder", () => {
        const flowGraph = triggeredFlowGraph()

        expect(VueFlowUtils.getTriggersJoinAlias(
            flowGraph.clusters, flowGraph.nodes, flowGraph.edges, new Set(), {[TRIGGERS_END]: "root.Triggers"},
        )).toEqual({})
    })

    test("still collapses the triggers lane into its placeholder", () => {
        const flowGraph = triggeredFlowGraph()
        // The state Topology.vue#collapseCluster builds for the triggers box.
        const edgeReplacer = {
            [TRIGGERS_CLUSTER]: "root.Triggers",
            [TRIGGERS_START]: "root.Triggers",
            [TRIGGERS_END]: "root.Triggers",
            [TRIGGER_UID]: "root.Triggers",
        }
        const elements = asElements(VueFlowUtils.generateGraph(
            "vfid", "flow", "ns", flowGraph, undefined, [TRIGGERS_START, TRIGGER_UID, TRIGGERS_END, TRIGGERS_CLUSTER],
            false, edgeReplacer, new Set(["root.Triggers"]), [], false, true, false,
        ) ?? [])

        // The lane is gone, so nothing is folded and the flow root still carries the chain.
        expect(elements.find((element) => element.id === "root.Triggers")?.type).toBe("collapsedcluster")
        expect(edgePairs(elements)).toContain(`root.Triggers|${FLOW_ROOT}`)
        expect(edgePairs(elements)).toContain(`${FLOW_ROOT}|root.notify_team`)
    })

    test("folds the flow root and a flowable's gate node in the same graph", () => {
        const withFlowable = triggeredFlowGraph({
            nodes: [
                {
                    uid: "root.seq",
                    type: "io.kestra.core.models.hierarchies.GraphTask",
                    task: {id: "seq", type: "io.kestra.plugin.core.flow.Sequential", namespace: "ns", flowId: "flow"},
                },
                {uid: "root.seq.root-2", type: "io.kestra.core.models.hierarchies.GraphClusterRoot"},
                {
                    uid: "root.seq.child",
                    type: "io.kestra.core.models.hierarchies.GraphTask",
                    task: {id: "child", type: "io.kestra.plugin.core.log.Log", namespace: "ns", flowId: "flow"},
                },
                {uid: "root.seq.end-2", type: "io.kestra.core.models.hierarchies.GraphClusterEnd"},
            ],
            edges: [
                {source: "root.notify_team", target: "root.seq"},
                {source: "root.seq", target: "root.seq.root-2"},
                {source: "root.seq.root-2", target: "root.seq.child"},
                {source: "root.seq.child", target: "root.seq.end-2"},
            ],
        })
        withFlowable.clusters.push({
            cluster: {
                uid: CLUSTER_PREFIX + "root.seq",
                type: "io.kestra.core.models.hierarchies.GraphCluster",
                taskNode: {uid: "root.seq", task: {id: "seq", type: "io.kestra.plugin.core.flow.Sequential"}},
            },
            nodes: ["root.seq", "root.seq.root-2", "root.seq.child", "root.seq.end-2"],
            parents: [],
            start: "root.seq.root-2",
            end: "root.seq.end-2",
        } as unknown as VueFlowUtils.FlowGraph["clusters"][number])

        const elements = generateTriggered(withFlowable)
        const ids = elements.map((element) => element.id)

        // Neither alias map clobbers the other: both redundant nodes are gone, the lane keeps its own.
        expect(ids).not.toContain(FLOW_ROOT)
        expect(ids).not.toContain("root.seq")
        expect(ids).toContain(TRIGGERS_END)
        expect(elements.find((element) => element.id === "root.seq.child")?.parentNode)
            .toBe(CLUSTER_PREFIX + "root.seq")
    })
})

describe("generateGraph collapsed nested clusters", () => {
    const nestedClustersGraph = {
        nodes: [
            {
                uid: "root.outer",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "outer", type: "io.kestra.plugin.core.flow.Sequential"},
            },
            {
                uid: "root.outer.inner",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "inner", type: "io.kestra.plugin.core.flow.Sequential"},
            },
            {
                uid: "root.outer.inner.task",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "task", type: "io.kestra.plugin.core.log.Log"},
            },
        ],
        edges: [
            {source: "root.outer", target: "root.outer.inner", relation: {relationType: "SEQUENTIAL"}},
            {source: "root.outer.inner", target: "root.outer.inner.task", relation: {relationType: "SEQUENTIAL"}},
        ],
        clusters: [
            {
                cluster: {
                    uid: "cluster_root.outer",
                    type: "io.kestra.core.models.hierarchies.GraphCluster",
                    taskNode: {uid: "root.outer", task: {id: "outer", type: "io.kestra.plugin.core.flow.Sequential"}},
                },
                nodes: ["root.outer.inner"],
                parents: [],
            },
            {
                cluster: {
                    uid: "cluster_root.outer.inner",
                    type: "io.kestra.core.models.hierarchies.GraphCluster",
                    taskNode: {uid: "root.outer.inner", task: {id: "inner", type: "io.kestra.plugin.core.flow.Sequential"}},
                },
                nodes: ["root.outer.inner.task"],
                parents: ["cluster_root.outer"],
            },
        ],
    } as unknown as VueFlowUtils.FlowGraph

    test("does not render a nested cluster when it is absorbed by a collapsed parent", () => {
        // Simulates the edge replacer state when the outer cluster is collapsed.
        // Even if the inner cluster is marked as collapsed in the Set, it should not render
        // because its edges are replaced to point to the parent collapsed node.
        const edgeReplacer = {
            "cluster_root.outer": "root.outer",
            "cluster_root.outer.inner": "root.outer",
            "root.outer.inner": "root.outer",
            "root.outer.inner.task": "root.outer",
        }

        const hiddenNodes = [
            "root.outer.inner", "cluster_root.outer.inner", "root.outer.inner.task", "cluster_root.outer",
        ]

        const collapsed = new Set(["root.outer", "root.outer.inner"])

        const elements = VueFlowUtils.generateGraph(
            "vfid", "flow", "ns", nestedClustersGraph, undefined, hiddenNodes, false, edgeReplacer, collapsed, [], false, true, false,
        ) ?? []


        const outerCollapsed = asElements(elements).find(e => e.id === "root.outer" && e.type === "collapsedcluster")
        expect(outerCollapsed).toBeDefined()

        const innerCollapsed = asElements(elements).find(e => e.id === "root.outer.inner" && e.type === "collapsedcluster")
        expect(innerCollapsed).toBeUndefined()

        const innerCluster = asElements(elements).find(e => e.id === "cluster_root.outer.inner")
        expect(innerCluster).toBeUndefined()
    })

    test("keeps the execution id on a collapsed flowable lane", () => {
        const executedGraph = {
            ...nestedClustersGraph,
            nodes: nestedClustersGraph.nodes.map((node) =>
                node.uid === "root.outer" ? {...node, executionId: "execution-id"} : node,
            ),
        } as unknown as VueFlowUtils.FlowGraph

        const elements = VueFlowUtils.generateGraph(
            "vfid", "flow", "ns", executedGraph, undefined, ["root.outer.inner", "cluster_root.outer.inner", "root.outer.inner.task", "cluster_root.outer"], false, {}, new Set(["root.outer"]), [], false, true, false,
        ) ?? []

        const outerCollapsed = asElements(elements).find(e => e.id === "root.outer" && e.type === "collapsedcluster")
        expect(outerCollapsed?.data?.executionId).toBe("execution-id")
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

    test("does not render the flowable's own gate node — the lane header is its one rendering", () => {
        expect(generate().some((e) => e.id === "root.parallel_task")).toBe(false)
    })

    test("gives the cluster its lane-header data: type, id and child count come from one place", () => {
        const cluster = generate().find((e) => e.id === "cluster_root.parallel_task")

        expect(cluster?.data?.isFlowableLane).toBe(true)
        expect(cluster?.data?.taskNode).toMatchObject({uid: "root.parallel_task"})
        expect(cluster?.data?.childTaskIds).toEqual(["branch_a", "branch_b"])
    })

    // The gate used to render as a second blank dot right under the lane's own root dot, so
    // entering a flowable drew dot → arrow → dot, both filling the same gap (kestra-io/kestra#19787).
    test("re-anchors the gate's edges on the lane's root dot, leaving a single connector", () => {
        const edges = generate().filter((e) => e.source && e.target)

        expect(edges.map((e) => `${e.source}|${e.target}`)).toEqual([
            "root.root-1|root.parallel_task.branch_a",
            "root.root-1|root.parallel_task.branch_b",
        ])
    })

    // Regression: the collapsed placeholder shares the flowable's own uid, so the dot-sizing
    // override for a hidden gate node must not also catch it and shrink it to a 5x5 dot.
    test("sizes a collapsed lane like an ordinary task node", () => {
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
        expect(collapsed?.style).toMatchObject({width: `${NODE_SIZES.TASK_WIDTH}px`, height: `${NODE_SIZES.TASK_HEIGHT}px`})
        expect(collapsed?.data).toMatchObject({
            isFlowableLane: true,
            taskNode: {uid: "root.parallel_task"},
            childTaskIds: ["branch_a", "branch_b"],
        })
    })
})

describe("generateGraph loop return path", () => {
    const loopFlowGraph = (type: string) => ({
        nodes: [
            {uid: "root.root-1", type: "io.kestra.core.models.hierarchies.GraphClusterRoot"},
            {uid: "root.end-1", type: "io.kestra.core.models.hierarchies.GraphClusterEnd"},
            {
                uid: "root.loop",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "loop", type},
            },
            {
                uid: "root.loop.child",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "child", type: "io.kestra.plugin.core.log.Log"},
            },
        ],
        edges: [{source: "root.loop", target: "root.loop.child"}],
        clusters: [
            {
                cluster: {
                    uid: "cluster_root.loop",
                    type: "io.kestra.core.models.hierarchies.GraphCluster",
                    taskNode: {uid: "root.loop", task: {id: "loop", type}},
                },
                nodes: ["root.root-1", "root.end-1", "root.loop", "root.loop.child"],
                parents: [],
                start: "root.root-1",
                end: "root.end-1",
            },
        ],
    }) as unknown as VueFlowUtils.FlowGraph

    const generate = (type: string, isHorizontal = false) =>
        asElements(VueFlowUtils.generateGraph(
            "vfid", "flow", "ns", loopFlowGraph(type), undefined, [], isHorizontal, {}, new Set(), [], true, false, false,
        ) ?? [])

    test.each([
        ["io.kestra.plugin.core.flow.LoopUntil", false],
        ["io.kestra.plugin.core.flow.Loop", true],
    ])("runs the %s return path 12px inside its lane border (horizontal: %s)", (type, isHorizontal) => {
        const elements = generate(type, isHorizontal)
        const returnPath = elements.find((element) => element.data?.loopReturn)
        const cluster = elements.find((element) => element.id === "cluster_root.loop")
        const startDot = elements.find((element) => element.id === "root.root-1")
        const {x = 0, y = 0} = cluster?.position ?? {}
        const width = Number.parseFloat(cluster?.style?.width ?? "0")
        const height = Number.parseFloat(cluster?.style?.height ?? "0")

        expect(returnPath).toMatchObject({type: "edge", source: "root.end-1", target: "root.root-1"})
        expect(returnPath?.markerEnd).toBeDefined()
        expect(returnPath?.data?.loopReturn).toEqual({
            right: x + width - 12,
            bottom: y + height - 12,
            entry: {x: x + (startDot?.position?.x ?? 0) + 2.5, y: y + (startDot?.position?.y ?? 0) + 2.5},
        })
    })

    test("draws no return path for a flowable that does not loop", () => {
        const elements = generate("io.kestra.plugin.core.flow.Sequential")

        expect(elements.some((element) => element.data?.loopReturn)).toBe(false)
    })

    test("omits the return path when the loop lane is collapsed", () => {
        const hiddenNodes = ["root.root-1", "root.end-1", "root.loop", "root.loop.child", "cluster_root.loop"]
        const edgeReplacer = {
            "cluster_root.loop": "root.loop",
            "root.root-1": "root.loop",
            "root.end-1": "root.loop",
        }
        const elements = asElements(VueFlowUtils.generateGraph(
            "vfid", "flow", "ns", loopFlowGraph("io.kestra.plugin.core.flow.LoopUntil"), undefined, hiddenNodes, false,
            edgeReplacer, new Set(["root.loop"]), [], true, false, false,
        ) ?? [])

        expect(elements.some((element) => element.data?.loopReturn)).toBe(false)
    })
})

describe("linkedElements", () => {
    test("stops at a loop's return edge instead of following the cycle it closes", () => {
        const {setNodes, setEdges} = useVueFlow("linked-loop")
        setNodes(["start", "task", "end", "after"].map((id) => ({id, position: {x: 0, y: 0}})))
        setEdges([
            {id: "start|task", source: "start", target: "task"},
            {id: "task|end", source: "task", target: "end"},
            {id: "end|after", source: "end", target: "after"},
            {id: "loop-return|end|start", source: "end", target: "start", data: {loopReturn: {}}},
        ])

        const linked = VueFlowUtils.linkedElements("linked-loop", "task").map((element) => element?.id)

        expect(linked).toEqual(expect.arrayContaining(["start|task", "task|end", "end|after", "after"]))
        expect(linked).not.toContain("loop-return|end|start")
    })
})

describe("loopReturnPath", () => {
    const bounds = {right: 488, bottom: 288}

    test("hugs the right and bottom borders of a horizontal lane and enters the start marker from below", () => {
        const d = VueFlowUtils.loopReturnPath({x: 440, y: 150}, {...bounds, entry: {x: 60, y: 150}}, true)

        expect(d).toBe(
            "M440,150 L483,150 Q488,150 488,155 L488,283 Q488,288 483,288 L65,288 Q60,288 60,283 L60,162",
        )
    })

    test("enters the start marker of a vertical lane from the side, clear of the header above it", () => {
        const d = VueFlowUtils.loopReturnPath({x: 250, y: 240}, {...bounds, entry: {x: 250, y: 80}}, false)

        expect(d).toBe("M250,240 L250,283 Q250,288 255,288 L483,288 Q488,288 488,283 L488,85 Q488,80 483,80 L262,80")
    })
})

describe("edgeTurnPosition", () => {
    // Both ends are measured from the node, not from the border it crossed, so a plain midpoint
    // sits half of those insets away from the middle of the gap a reader sees.
    test("centres the turn on the visible gap, not on the path", () => {
        expect(edgeTurnPosition(100, 300, {gap: {leaving: 0, entering: 40}})).toBe(180)
        expect(edgeTurnPosition(100, 300, {gap: {leaving: 20, entering: 40}})).toBe(190)
    })

    test("keeps the turn clear of the straight run out of each handle", () => {
        expect(edgeTurnPosition(100, 300, {gap: {leaving: 0, entering: 400}})).toBe(120)
        expect(edgeTurnPosition(100, 300, {gap: {leaving: 400, entering: 0}})).toBe(280)
    })

    test("gives up when the two ends are too close to turn between", () => {
        expect(edgeTurnPosition(100, 130, {gap: {leaving: 0, entering: 10}})).toBeUndefined()
    })

    // An error branch turns level with the end that stays in the main column, so its long run
    // happens in its own column instead of across whatever lane sits between the two.
    test("turns at the main-column end of an error branch, whichever way it runs", () => {
        expect(edgeTurnPosition(100, 900, {bypass: "source"})).toBe(120)
        expect(edgeTurnPosition(100, 900, {bypass: "target"})).toBe(880)
        expect(edgeTurnPosition(900, 100, {bypass: "source"})).toBe(880)
        expect(edgeTurnPosition(900, 100, {bypass: "target"})).toBe(120)
    })

    test("leaves an ordinary edge to vue-flow's own midpoint", () => {
        expect(edgeTurnPosition(100, 300, {})).toBeUndefined()
    })
})

describe("fanOutSplitPosition", () => {
    test("sits halfway between the lane's marker and the split", () => {
        expect(fanOutSplitPosition(100, 300, 200)).toBe(150)
    })

    test("falls back to the path's own midpoint when the edge does not turn", () => {
        expect(fanOutSplitPosition(100, 300, undefined)).toBe(150)
    })
})

describe("pickFanOutAddEdges", () => {
    const parallelLane = {
        uid: "cluster_root.in_parallel",
        type: "io.kestra.core.models.hierarchies.GraphCluster",
        taskNode: {uid: "root.in_parallel", task: {type: "io.kestra.plugin.core.flow.Parallel"}},
    } satisfies Cluster
    const switchLane = {
        uid: "cluster_root.pick",
        type: "io.kestra.core.models.hierarchies.GraphCluster",
        taskNode: {uid: "root.pick", task: {type: "io.kestra.plugin.core.flow.Switch"}},
    } satisfies Cluster

    test("keeps one add button for a Parallel's branches", () => {
        const owners = VueFlowUtils.pickFanOutAddEdges(
            [
                {source: "root.in_parallel.root-1", target: "root.in_parallel.branch_a"},
                {source: "root.in_parallel.root-1", target: "root.in_parallel.branch_b"},
            ],
            {"root.in_parallel.root-1": parallelLane},
        )

        expect(owners.get("root.in_parallel.root-1")).toBe("root.in_parallel.branch_a")
    })

    // A Switch's cases are not interchangeable, so which edge the button sits on is the choice
    // the user is making — collapsing them would throw that away.
    test("leaves an ordered fan-out with a button per branch", () => {
        const owners = VueFlowUtils.pickFanOutAddEdges(
            [
                {source: "root.pick.root-1", target: "root.pick.french"},
                {source: "root.pick.root-1", target: "root.pick.german"},
            ],
            {"root.pick.root-1": switchLane},
        )

        expect(owners.size).toBe(0)
    })

    test("leaves a Parallel with a single child alone", () => {
        const owners = VueFlowUtils.pickFanOutAddEdges(
            [{source: "root.in_parallel.root-1", target: "root.in_parallel.branch_a"}],
            {"root.in_parallel.root-1": parallelLane},
        )

        expect(owners.size).toBe(0)
    })
})

describe("generateGraph lane header height vs a following sibling's clearance", () => {
    // Same Parallel lane as above, plus a plain sibling task right after it in the same rank
    // sequence — LANE_HEADER_HEIGHT is added to the cluster's box *after* dagre has already
    // spaced this sibling using the un-inflated height, so the header eats into that gap.
    const flowGraph = {
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
            {
                uid: "root.after_task",
                type: "io.kestra.core.models.hierarchies.GraphTask",
                task: {id: "after_task", type: "io.kestra.plugin.core.log.Log", namespace: "ns", flowId: "flow"},
            },
        ],
        edges: [
            {source: "root.root-1", target: "root.parallel_task", relation: {}},
            {source: "root.parallel_task", target: "root.parallel_task.branch_a", relation: {relationType: "PARALLEL"}},
            {source: "root.parallel_task", target: "root.parallel_task.branch_b", relation: {relationType: "PARALLEL"}},
            // Every branch feeds the cluster's own end marker — omitting this (as a real backend
            // graph never does) leaves `end-1` with no incoming edge, so dagre ranks it arbitrarily
            // instead of after the branches, and any "clearance" measured against it is meaningless.
            {source: "root.parallel_task.branch_a", target: "root.end-1", relation: {}},
            {source: "root.parallel_task.branch_b", target: "root.end-1", relation: {}},
            {source: "root.end-1", target: "root.after_task", relation: {relationType: "SEQUENTIAL"}},
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

    test("leaves a full rank separation below the lane, header included", () => {
        const elements = asElements(VueFlowUtils.generateGraph(
            "vfid", "flow", "ns", flowGraph, undefined, [], false, {}, new Set(), [], true, false, false,
        ) ?? [])

        const lane = elements.find((e) => e.id === "cluster_root.parallel_task")
        const sibling = elements.find((e) => e.id === "root.after_task")
        expect(lane?.position).toBeDefined()
        expect(sibling?.position).toBeDefined()

        const laneBottom = (lane!.position!.y) + parseFloat(String(lane!.style!.height))
        const siblingTop = sibling!.position!.y

        // The separation is widened by LANE_HEADER_HEIGHT precisely so the header the cluster box
        // grows by afterwards costs the gap nothing. Pinned exactly — not just "> 0" — so a bigger
        // header, or nesting that erodes the same gap, fails loudly here instead of crowding the
        // edge's add button against a lane border in the browser.
        expect(siblingTop - laneBottom).toBe(DAGRE_RANK_SEP)
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

    // The two halves the edge geometry relies on: an edge crossing a lane border carries how far
    // each of its ends sits inside that lane, and an edge into the error branch is marked so its
    // long run keeps out of the main column.
    test("marks the edge into the errors lane with the insets and the bypass it needs", () => {
        const elements = asElements(VueFlowUtils.generateGraph(
            "vfid", "flow", "ns", flowWithRootErrors, undefined, [], false, {}, new Set(), [], true, false, false,
        ) ?? [])

        const intoErrors = elements.find((e) => e.target === "root.error_handler")
        expect(intoErrors?.data?.bypass).toBe("source")
        expect(intoErrors?.data?.laneGap?.entering).toBeGreaterThan(0)
        expect(intoErrors?.data?.laneGap?.leaving).toBe(0)
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

describe("getNodeWidth / getNodeHeight (per node-kind footprint)", () => {
    const triggerNode = {uid: "root.Triggers.schedule", type: "io.kestra.core.models.hierarchies.GraphTrigger"}
    const taskNode = {uid: "root.a", type: "io.kestra.core.models.hierarchies.GraphTask"}

    test("sizes a trigger node against TRIGGER_HEIGHT/TRIGGER_WIDTH, not TASK_HEIGHT/TASK_WIDTH", () => {
        expect(VueFlowUtils.getNodeHeight(triggerNode)).toBe(NODE_SIZES.TRIGGER_HEIGHT)
        expect(VueFlowUtils.getNodeWidth(triggerNode)).toBe(NODE_SIZES.TRIGGER_WIDTH)
    })

    test("sizes a task node against TASK_HEIGHT/TASK_WIDTH", () => {
        expect(VueFlowUtils.getNodeHeight(taskNode)).toBe(NODE_SIZES.TASK_HEIGHT)
        expect(VueFlowUtils.getNodeWidth(taskNode)).toBe(NODE_SIZES.TASK_WIDTH)
    })
})

describe("buildEffectiveGetNodeDimensions (footprint invariance)", () => {
    const taskNode = {uid: "root.a", type: "io.kestra.core.models.hierarchies.GraphTask"}

    test("returns the compact task footprint with no execution, with no room for a duration bar", () => {
        const getDimensions = VueFlowUtils.buildEffectiveGetNodeDimensions(false)
        const dimensions = getDimensions(taskNode, VueFlowUtils.getNodeWidth, VueFlowUtils.getNodeHeight)

        expect(dimensions).toEqual({width: NODE_SIZES.TASK_WIDTH, height: 56})
    })

    test("reserves the duration bar row for every task node once an execution is loaded", () => {
        const withExecution = VueFlowUtils.buildEffectiveGetNodeDimensions(true)(taskNode, VueFlowUtils.getNodeWidth, VueFlowUtils.getNodeHeight)

        expect(withExecution).toEqual({width: 273, height: 80})
    })

    test("gives a collapsed lane the same footprint as a task node in an execution", () => {
        const lane = {uid: "root.seq", type: "collapsedcluster", isFlowableLane: true}
        const getDimensions = VueFlowUtils.buildEffectiveGetNodeDimensions(true)

        expect(getDimensions(lane, VueFlowUtils.getNodeWidth, VueFlowUtils.getNodeHeight).height)
            .toBe(getDimensions(taskNode, VueFlowUtils.getNodeWidth, VueFlowUtils.getNodeHeight).height)
    })

    test("keeps a trigger at its own height in an execution", () => {
        const trigger = {uid: "Triggers.cron", type: "io.kestra.core.models.hierarchies.GraphTrigger"}
        const dimensions = VueFlowUtils.buildEffectiveGetNodeDimensions(true)(trigger, VueFlowUtils.getNodeWidth, VueFlowUtils.getNodeHeight)

        expect(dimensions.height).toBe(NODE_SIZES.TRIGGER_HEIGHT)
    })

    test("is deterministic: calling it repeatedly for the same node never drifts", () => {
        const getDimensions = VueFlowUtils.buildEffectiveGetNodeDimensions(false)
        const results = Array.from({length: 5}, () => getDimensions(taskNode, VueFlowUtils.getNodeWidth, VueFlowUtils.getNodeHeight))

        expect(new Set(results.map((r) => JSON.stringify(r))).size).toBe(1)
    })
})

describe("generateGraph horizontal lane headers", () => {
    const hierarchy = "io.kestra.core.models.hierarchies."
    const task = (uid: string, type = "io.kestra.plugin.core.log.Log") =>
        ({uid, type: hierarchy + "GraphTask", task: {id: uid.split(".").pop(), type}})
    const dot = (uid: string, kind: "Root" | "End") => ({uid, type: hierarchy + "GraphCluster" + kind})
    const edge = (source: string, target: string) => ({source, target, relation: {}})
    const lane = (uid: string, nodes: string[], parents: string[]) => ({
        cluster: {uid: "cluster_" + uid, type: hierarchy + "GraphCluster", taskNode: task(uid, "io.kestra.plugin.core.flow.Loop")},
        nodes: [uid + ".r", uid + ".e", ...nodes],
        parents,
        start: uid + ".r",
        end: uid + ".e",
    })

    const generate = (flowGraph: object) => asElements(VueFlowUtils.generateGraph(
        "vfid", "flow", "ns", flowGraph as VueFlowUtils.FlowGraph, undefined, [], true, {}, new Set(), [], true, false, false,
    ) ?? [])

    const absoluteBox = (elements: GeneratedElement[], id: string): {top: number; bottom: number} => {
        const element = elements.find((e) => e.id === id)!
        const parentTop = element.parentNode ? absoluteBox(elements, element.parentNode).top : 0
        const top = parentTop + element.position!.y
        return {top, bottom: top + parseFloat(String(element.style!.height))}
    }

    test("keeps a lane nested in another one inside its parent", () => {
        const elements = generate({
            nodes: [
                dot("root.r", "Root"), dot("root.e", "End"),
                dot("root.outer.r", "Root"), dot("root.outer.e", "End"), task("root.outer", "io.kestra.plugin.core.flow.Loop"),
                dot("root.outer.inner.r", "Root"), dot("root.outer.inner.e", "End"), task("root.outer.inner", "io.kestra.plugin.core.flow.Loop"),
                task("root.outer.inner.a"), task("root.outer.inner.b"),
            ],
            edges: [
                edge("root.r", "root.outer.r"), edge("root.outer.r", "root.outer"), edge("root.outer", "root.outer.inner.r"),
                edge("root.outer.inner.r", "root.outer.inner"), edge("root.outer.inner", "root.outer.inner.a"),
                edge("root.outer.inner.a", "root.outer.inner.b"), edge("root.outer.inner.b", "root.outer.inner.e"),
                edge("root.outer.inner.e", "root.outer.e"), edge("root.outer.e", "root.e"),
            ],
            clusters: [
                lane("root.outer", ["root.outer", "cluster_root.outer.inner", "root.outer.inner.r", "root.outer.inner.e"], []),
                lane("root.outer.inner", ["root.outer.inner", "root.outer.inner.a", "root.outer.inner.b"], ["cluster_root.outer"]),
            ],
        })

        const outer = absoluteBox(elements, "cluster_root.outer")
        const inner = absoluteBox(elements, "cluster_root.outer.inner")
        expect(inner.top).toBeGreaterThanOrEqual(outer.top + NODE_SIZES.LANE_HEADER_HEIGHT)
        expect(inner.bottom).toBeLessThanOrEqual(outer.bottom)
    })

    test("moves a branch below a lane down as one row", () => {
        const chain = ["c1", "c2", "c3", "c4", "c5"].map((id) => "root.par." + id)
        const elements = generate({
            nodes: [
                dot("root.r", "Root"), dot("root.e", "End"),
                dot("root.par.r", "Root"), dot("root.par.e", "End"), task("root.par", "io.kestra.plugin.core.flow.Parallel"),
                dot("root.par.loop.r", "Root"), dot("root.par.loop.e", "End"), task("root.par.loop", "io.kestra.plugin.core.flow.Loop"),
                task("root.par.loop.x"), ...chain.map((uid) => task(uid)),
            ],
            edges: [
                edge("root.r", "root.par.r"), edge("root.par.r", "root.par"),
                edge("root.par", "root.par.loop.r"), edge("root.par.loop.r", "root.par.loop"), edge("root.par.loop", "root.par.loop.x"),
                edge("root.par.loop.x", "root.par.loop.e"), edge("root.par.loop.e", "root.par.e"),
                edge("root.par", chain[0]), ...chain.slice(1).map((uid, i) => edge(chain[i], uid)), edge(chain[chain.length - 1], "root.par.e"),
                edge("root.par.e", "root.e"),
            ],
            clusters: [
                {...lane("root.par", ["root.par", ...chain, "cluster_root.par.loop", "root.par.loop.r", "root.par.loop.e"], []), cluster: {uid: "cluster_root.par", type: hierarchy + "GraphCluster", taskNode: task("root.par", "io.kestra.plugin.core.flow.Parallel")}},
                lane("root.par.loop", ["root.par.loop", "root.par.loop.x"], ["cluster_root.par"]),
            ],
        })

        const loop = absoluteBox(elements, "cluster_root.par.loop")
        const rowTops = chain.map((uid) => absoluteBox(elements, uid).top)
        expect(new Set(rowTops).size).toBe(1)
        expect(rowTops[0]).toBeGreaterThan(loop.bottom)
    })
})

describe("generateGraph collapsed cluster nested in an expanded one", () => {
    const task = (uid: string, type: string) => ({uid, type: "io.kestra.core.models.hierarchies.GraphTask", task: {id: uid.split(".").pop(), type}})
    const dot = (uid: string, kind: "Root" | "End") => ({uid, type: `io.kestra.core.models.hierarchies.GraphCluster${kind}`})
    const cluster = (uid: string, taskNode: ReturnType<typeof task>, nodes: string[], parents: string[]) => ({
        cluster: {uid, type: "io.kestra.core.models.hierarchies.GraphCluster", taskNode},
        nodes,
        parents,
        start: nodes.find((n) => n.includes(".root-")),
        end: nodes.find((n) => n.includes(".end-")),
    })

    const loop = task("root.loop", "io.kestra.plugin.core.flow.Loop")
    const inner = task("root.loop.inner", "io.kestra.plugin.core.flow.Sequential")
    const log = task("root.loop.inner.log", "io.kestra.plugin.core.log.Log")

    const flowGraph = {
        nodes: [
            dot("root.root-a", "Root"), dot("root.end-a", "End"),
            dot("root.loop.root-b", "Root"), dot("root.loop.end-b", "End"), loop,
            dot("root.loop.inner.root-c", "Root"), dot("root.loop.inner.end-c", "End"), inner, log,
        ],
        edges: [
            {source: "root.root-a", target: "root.loop.root-b"},
            {source: "root.loop.root-b", target: "root.loop"},
            {source: "root.loop", target: "root.loop.inner.root-c"},
            {source: "root.loop.inner.root-c", target: "root.loop.inner"},
            {source: "root.loop.inner", target: "root.loop.inner.log"},
            {source: "root.loop.inner.log", target: "root.loop.inner.end-c"},
            {source: "root.loop.inner.end-c", target: "root.loop.end-b"},
            {source: "root.loop.end-b", target: "root.end-a"},
        ],
        clusters: [
            cluster("cluster_root.loop", loop, [
                "root.loop.root-b", "root.loop.end-b", "root.loop", "cluster_root.loop.inner",
                "root.loop.inner.root-c", "root.loop.inner.end-c",
            ], []),
            cluster("cluster_root.loop.inner", inner, [
                "root.loop.inner.root-c", "root.loop.inner.end-c", "root.loop.inner", "root.loop.inner.log",
            ], ["cluster_root.loop"]),
        ],
    } as unknown as VueFlowUtils.FlowGraph

    test("keeps the collapsed placeholder inside its parent cluster", () => {
        // State left by collapsing `inner` from the canvas.
        const innerCluster = flowGraph.clusters![1]
        const hiddenNodes = [...innerCluster.nodes, innerCluster.cluster.uid]
        const edgeReplacer = {
            "root.loop.inner": "root.loop.inner",
            "cluster_root.loop.inner": "root.loop.inner",
            "root.loop.inner.root-c": "root.loop.inner",
            "root.loop.inner.end-c": "root.loop.inner",
            "root.loop.inner.log": "root.loop.inner",
        }

        const elements = asElements(VueFlowUtils.generateGraph(
            "vfid", "flow", "ns", flowGraph, undefined, hiddenNodes, false, edgeReplacer, new Set(["root.loop.inner"]), [], false, true, false,
        ))

        const dagreGraph = VueFlowUtils.generateDagreGraph(
            flowGraph, hiddenNodes, false, edgeReplacer, new Set(["root.loop.inner"]), [],
        )

        expect(elements.find((e) => e.id === "root.loop.inner" && e.type === "collapsedcluster")!.parentNode).toBe("cluster_root.loop")
        expect(dagreGraph.parent("root.loop.inner")).toBe("cluster_root.loop")
    })

    test("does not offer to collapse a cluster with no task of its own", () => {
        const empty = task("root.approval", "io.kestra.plugin.core.flow.Approval")
        const elements = asElements(VueFlowUtils.generateGraph(
            "vfid", "flow", "ns", {
                nodes: [dot("root.approval.root-d", "Root"), dot("root.approval.end-d", "End"), empty],
                edges: [{source: "root.approval.root-d", target: "root.approval"}],
                clusters: [cluster("cluster_root.approval", empty, ["root.approval.root-d", "root.approval.end-d", "root.approval"], [])],
            } as unknown as VueFlowUtils.FlowGraph, undefined, [], false, {}, new Set(), [], false, true, false,
        ))

        expect(elements.find((e) => e.id === "cluster_root.approval")!.data!.collaspsible).toBe(false)
    })
})

describe("nodeColor", () => {
    test("returns success for triggers root node and trigger nodes", () => {
        expect(nodeColor({uid: "root.Triggers", type: "task"}, new Set())).toBe("success")
        expect(nodeColor({uid: "root.cron", type: "io.kestra.core.models.hierarchies.GraphTrigger"}, new Set())).toBe("success")
    })

    test("returns flowable-task for a collapsed cluster and primary for a subflow task", () => {
        expect(nodeColor({uid: "root.cluster", type: "collapsedcluster"}, new Set())).toBe("flowable-task")
        expect(nodeColor({uid: "root.subflow", type: "io.kestra.core.models.hierarchies.SubflowGraphTask"}, new Set())).toBe("primary")
    })

    test("returns danger for error branch, warning for finally branch, and blue for collapsed", () => {
        expect(nodeColor({uid: "root.err", type: "task", branchType: BranchType.ERROR}, new Set())).toBe("danger")
        expect(nodeColor({uid: "root.fin", type: "task", branchType: BranchType.FINALLY}, new Set())).toBe("warning")
        expect(nodeColor({uid: "root.col", type: "task"}, new Set(["root.col"]))).toBe("blue")
        expect(nodeColor({uid: "root.normal", type: "task"}, new Set())).toBe("default")
    })

    test("pins branch order: earlier branch wins when a node matches multiple conditions", () => {
        expect(nodeColor({uid: "root.Triggers", type: "task", branchType: BranchType.ERROR}, new Set())).toBe("success")
        expect(nodeColor({uid: "root.cron", type: "io.kestra.core.models.hierarchies.GraphTrigger", branchType: BranchType.ERROR}, new Set())).toBe("success")
        expect(nodeColor({uid: "root.col", type: "collapsedcluster"}, new Set(["root.col"]))).toBe("flowable-task")
        expect(nodeColor({uid: "root.sub", type: "io.kestra.core.models.hierarchies.SubflowGraphTask", branchType: BranchType.ERROR}, new Set())).toBe("primary")
        expect(nodeColor({uid: "root.err", type: "task", branchType: BranchType.ERROR}, new Set(["root.err"]))).toBe("danger")
        expect(nodeColor({uid: "root.fin", type: "task", branchType: BranchType.FINALLY}, new Set(["root.fin"]))).toBe("warning")
    })
})

describe("computeClusterColor", () => {
    test("returns triggers for the triggers cluster", () => {
        expect(computeClusterColor({
            uid: CLUSTER_PREFIX + "root.Triggers",
            type: "io.kestra.core.models.hierarchies.GraphCluster",
            taskNode: null,
        })).toBe("triggers")
    })

    test("returns subflow for a subflow cluster", () => {
        expect(computeClusterColor({
            uid: "cluster_root.subflow",
            type: "io.kestra.core.models.hierarchies.SubflowGraphCluster",
            taskNode: null,
        })).toBe("subflow")
    })

    test("returns errors for an error branch cluster", () => {
        expect(computeClusterColor({
            uid: "cluster_root.errors",
            type: "io.kestra.core.models.hierarchies.GraphCluster",
            taskNode: null,
            branchType: BranchType.ERROR,
        })).toBe("errors")
    })

    test("returns flowable-task for standard clusters", () => {
        expect(computeClusterColor({
            uid: "cluster_root.parallel",
            type: "io.kestra.core.models.hierarchies.GraphCluster",
            taskNode: null,
        })).toBe("flowable-task")
    })

    test("pins branch order: earlier branch wins when a cluster matches multiple conditions", () => {
        expect(computeClusterColor({
            uid: CLUSTER_PREFIX + "root.Triggers",
            type: "io.kestra.core.models.hierarchies.SubflowGraphCluster",
            taskNode: null,
            branchType: BranchType.ERROR,
        })).toBe("triggers")

        expect(computeClusterColor({
            uid: "cluster_root.subflow",
            type: "io.kestra.core.models.hierarchies.SubflowGraphCluster",
            taskNode: null,
            branchType: BranchType.ERROR,
        })).toBe("subflow")
    })
})

describe("getEdgeColorToken", () => {
    test("maps danger and warning to their css variables", () => {
        expect(getEdgeColorToken("danger")).toBe("var(--ks-border-error)")
        expect(getEdgeColorToken("warning")).toBe("var(--ks-status-warning)")
    })

    test("returns fallback token for null and unrecognized colors", () => {
        expect(getEdgeColorToken(null)).toBe("var(--ks-topology-dash)")
        expect(getEdgeColorToken("unrecognized")).toBe("var(--ks-topology-dash)")
    })
})

describe("getEdgeColor", () => {
    test("picks danger when source or target is an error branch", () => {
        const edge = {source: "root.a", target: "root.b"}
        const nodes: Record<string, MinimalNode> = {
            "root.a": {uid: "root.a", type: "task"},
            "root.b": {uid: "root.b", type: "task", branchType: BranchType.ERROR},
        }
        expect(getEdgeColor(edge, nodes, {})).toBe("danger")
    })

    test("picks warning when source or target is a finally branch", () => {
        const edge = {source: "root.a", target: "root.b"}
        const nodes: Record<string, MinimalNode> = {
            "root.a": {uid: "root.a", type: "task", branchType: BranchType.FINALLY},
            "root.b": {uid: "root.b", type: "task"},
        }
        expect(getEdgeColor(edge, nodes, {})).toBe("warning")
    })

    test("returns null for standard edges", () => {
        const edge = {source: "root.a", target: "root.b"}
        const nodes: Record<string, MinimalNode> = {
            "root.a": {uid: "root.a", type: "task"},
            "root.b": {uid: "root.b", type: "task"},
        }
        expect(getEdgeColor(edge, nodes, {})).toBeNull()
    })

    test("inherits branch color from parent cluster", () => {
        const edge = {source: "root.parent.a", target: "root.parent.b"}
        const nodes: Record<string, MinimalNode> = {
            "root.parent.a": {uid: "root.parent.a", type: "task"},
            "root.parent.b": {uid: "root.parent.b", type: "task"},
        }
        const clusters: Record<string, Cluster> = {
            "root.parent": {
                uid: "cluster_root.parent",
                type: "io.kestra.core.models.hierarchies.GraphCluster",
                taskNode: null,
                branchType: BranchType.ERROR,
            },
        }
        expect(getEdgeColor(edge, nodes, clusters)).toBe("danger")
    })
})
