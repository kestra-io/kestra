import {test, expect, describe} from "vitest"
import * as VueFlowUtils from "../../../src/utils/vueFlowUtils.ts"
import {edgeTurnPosition, fanOutSplitPosition, type Cluster} from "../../../src/utils/vueFlowUtils.ts"
import {DAGRE_RANK_SEP, NODE_SIZES} from "../../../src/utils/constants.ts"

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
        expect(collapsed?.style).toMatchObject({width: "218px", height: "56px"})
        expect(collapsed?.data).toMatchObject({
            isFlowableLane: true,
            taskNode: {uid: "root.parallel_task"},
            childTaskIds: ["branch_a", "branch_b"],
        })
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

    test("returns the constant task footprint with no execution", () => {
        const getDimensions = VueFlowUtils.buildEffectiveGetNodeDimensions(false)
        const dimensions = getDimensions(taskNode, VueFlowUtils.getNodeWidth, VueFlowUtils.getNodeHeight)

        expect(dimensions).toEqual({width: NODE_SIZES.TASK_WIDTH, height: NODE_SIZES.TASK_HEIGHT})
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
