import {MarkerType, Position, useVueFlow} from "@vue-flow/core"
import type {GraphNode, GraphEdge, Elements} from "@vue-flow/core"
import * as dagre from "dagre"
import * as Utils from "./utils"
import {CLUSTER_PREFIX, NODE_SIZES} from "./constants"
import isEqual from "lodash/isEqual"

const TRIGGERS_NODE_UID = "root.Triggers"

enum BranchType {
    ERROR = "ERROR",
    FINALLY = "FINALLY",
    AFTER_EXECUTION = "AFTER_EXECUTION",
}

interface MinimalNode {
    unused?: boolean;
    executionId?: string;
    branchType?: BranchType;
    uid: string;
    type: string;
    task?: {
        id?: string;
        type: string;
        namespace: string;
        flowId: string;
    };
}

interface Cluster {
    uid: string;
    type: string;
    nodes: MinimalNode[];
    taskNode: {
        uid: string;
        task: {
            type: string;
            namespace: string;
            flowId: string;
        };
    };
    branchType: BranchType;
}

export interface FlowGraph {
    nodes: MinimalNode[];
    clusters: {
        cluster: Cluster;
        nodes: string[];
        parents: {
            uid: string;
        }[];
    }[];
    edges: GraphEdge[];
}

type EdgeReplacer = Record<string, string>;

export function predecessorsEdge(vueFlowId: string, nodeUid: string): GraphEdge[] {
    const {getEdges} = useVueFlow(vueFlowId)
    const nodes = []
    for (const edge of getEdges.value) {
        if (edge.target === nodeUid) {
            nodes.push(edge)
            const recursiveEdge = predecessorsEdge(vueFlowId, edge.source)
            if (recursiveEdge.length > 0) {
                nodes.push(...recursiveEdge)
            }
        }
    }
    return nodes
}

export function successorsEdge(vueFlowId: string, nodeUid: string): GraphEdge[] {
    const {getEdges} = useVueFlow(vueFlowId)
    const nodes = []
    for (const edge of getEdges.value) {
        if (edge.source === nodeUid) {
            nodes.push(edge)
            const recursiveEdge = successorsEdge(vueFlowId, edge.target)
            if (recursiveEdge.length > 0) {
                nodes.push(...recursiveEdge)
            }
        }
    }
    return nodes
}

export function predecessorsNode(vueFlowId: string, nodeUid: string): (GraphEdge | GraphNode)[] {
    const {getEdges, findNode} = useVueFlow(vueFlowId)
    const foundNode = findNode(nodeUid)
    const nodes: (GraphEdge | GraphNode)[] = foundNode ? [foundNode] : []
    for (const edge of getEdges.value) {
        if (edge.target === nodeUid) {
            nodes.push(edge.sourceNode)
            const recursiveEdge = predecessorsNode(vueFlowId, edge.source)
            if (recursiveEdge.length > 0) {
                nodes.push(...recursiveEdge)
            }
        }
    }
    return nodes
}

export function successorsNode(vueFlowId: string, nodeUid: string) {
    const {getEdges, findNode} = useVueFlow(vueFlowId)
    const nodes = [findNode(nodeUid)]
    for (const edge of getEdges.value) {
        if (edge.source === nodeUid) {
            nodes.push(edge.targetNode)
            const recursiveEdge = successorsNode(vueFlowId, edge.target)
            if (recursiveEdge.length > 0) {
                nodes.push(...recursiveEdge)
            }
        }
    }
    return nodes
}

export function linkedElements(vueFlowId: string, nodeUid: string) {
    return [
        ...predecessorsEdge(vueFlowId, nodeUid),
        ...predecessorsNode(vueFlowId, nodeUid),
        ...successorsEdge(vueFlowId, nodeUid),
        ...successorsNode(vueFlowId, nodeUid),
    ]
}

export function generateDagreGraph(
    flowGraph: {nodes: any; clusters: any; edges: any},
    hiddenNodes: string[],
    isHorizontal: boolean,
    edgeReplacer: EdgeReplacer,
    collapsed: Set<string>,
    clusterToNode: MinimalNode[],
    getNodeDimensions: (
        node: MinimalNode,
        widthFn: (node: MinimalNode) => number,
        heightFn: (node: MinimalNode) => number,
    ) => {width: number; height: number} = (node, widthFn, heightFn) => ({
        width: widthFn(node),
        height: heightFn(node),
    }),
) {
    const dagreGraph = new dagre.graphlib.Graph({compound: true})
    dagreGraph.setDefaultEdgeLabel(() => ({}))
    dagreGraph.setGraph({rankdir: isHorizontal ? "LR" : "TB"})

    for (const node of flowGraph.nodes) {
        if (!hiddenNodes.includes(node.uid)) {
            dagreGraph.setNode(node.uid, getNodeDimensions(node, getNodeWidth, getNodeHeight))
        }
    }

    for (const cluster of flowGraph.clusters || []) {
        const nodeUid = cluster.cluster.uid.replace(CLUSTER_PREFIX, "")
        if (collapsed.has(nodeUid)) {
            const node = {uid: nodeUid, type: "collapsedcluster"}
            const dimensions = getNodeDimensions(node, getNodeWidth, getNodeHeight)
            dagreGraph.setNode(nodeUid, dimensions)
            clusterToNode.push(node)
            continue
        }
        if (!edgeReplacer[cluster.cluster.uid]) {
            dagreGraph.setNode(cluster.cluster.uid, {clusterLabelPos: "top"})
            for (const node of cluster.nodes || []) {
                if (!hiddenNodes.includes(node)) {
                    dagreGraph.setParent(node, cluster.cluster.uid)
                }
            }
        }
        if (cluster.parents) {
            const nodeChild = edgeReplacer[cluster.cluster.uid]
                ? edgeReplacer[cluster.cluster.uid]
                : cluster.cluster.uid
            if (!hiddenNodes.includes(nodeChild)) {
                dagreGraph.setParent(nodeChild, cluster.parents[cluster.parents.length - 1])
            }
        }
    }

    for (const edge of flowGraph.edges || []) {
        const newEdge = replaceIfCollapsed(edge.source, edge.target, edgeReplacer, hiddenNodes, collapsed)
        if (newEdge) {
            dagreGraph.setEdge(newEdge.source, newEdge.target)
        }
    }

    dagre.layout(dagreGraph)
    return dagreGraph
}

export function getNodePosition(
    n: {x: number; y: number; width: number; height: number},
    parent?: {x: number; y: number; width: number; height: number},
) {
    const position = {x: n.x - n.width / 2, y: n.y - n.height / 2}
    if (parent) {
        const parentPosition = getNodePosition(parent)
        position.x = position.x - parentPosition.x
        position.y = position.y - parentPosition.y
    }
    return position
}

export function getNodeWidth(node: MinimalNode) {
    return isTaskNode(node) || isTriggerNode(node) || isCustomNode(node)
        ? NODE_SIZES.TASK_WIDTH
        : isCollapsedCluster(node)
          ? NODE_SIZES.COLLAPSED_CLUSTER_WIDTH
          : NODE_SIZES.DOT_WIDTH
}

export function getNodeHeight(node: MinimalNode) {
    return isTaskNode(node) || isTriggerNode(node)
        ? NODE_SIZES.TASK_HEIGHT
        : isCollapsedCluster(node)
          ? NODE_SIZES.COLLAPSED_CLUSTER_HEIGHT
          : NODE_SIZES.DOT_HEIGHT
}

export function isTaskNode(node: MinimalNode) {
    return ["GraphTask", "SubflowGraphTask$1"].some((t) => node.type.endsWith(t))
}

export function isTriggerNode(node: MinimalNode) {
    return node.type.endsWith("GraphTrigger")
}

export function isCustomNode(node: MinimalNode) {
    return node.type.endsWith("CustomGraphNode")
}

export function isCollapsedCluster(node: MinimalNode) {
    return node.type === "collapsedcluster"
}

export function replaceIfCollapsed(
    source: string,
    target: string,
    edgeReplacer: EdgeReplacer,
    hiddenNodes: string[],
    collapsed: Set<string>,
) {
    const newSource = edgeReplacer[source] ? edgeReplacer[source] : source
    const newTarget = edgeReplacer[target] ? edgeReplacer[target] : target
    const isHidden = (uid: string) => hiddenNodes.includes(uid) && !collapsed.has(uid)
    if (newSource === newTarget || isHidden(newSource) || isHidden(newTarget)) {
        return null
    }
    return {target: newTarget, source: newSource}
}

export function cleanGraph(vueflowId: string) {
    const {getEdges, getNodes, getElements, removeEdges, removeNodes, removeSelectedElements} =
        useVueFlow(vueflowId)
    removeEdges(getEdges.value)
    removeNodes(getNodes.value)
    removeSelectedElements(getElements.value)
}

export function flowHaveTasks(source: string): boolean {
    if (!source) return false
    // Check if the root-level `tasks` key exists and has at least one list item
    const match = source.match(/^tasks\s*:\s*\r?\n([\s\S]*?)(?=^\S|$(?![\r\n]))/m)
    return match != null && /^\s+-/m.test(match[1] ?? "")
}

export function nodeColor(node: MinimalNode, collapsed: Set<string>) {
    if (node.uid === TRIGGERS_NODE_UID) return "success"
    if (isTriggerNode(node)) return "success"
    if (isCollapsedCluster(node)) return "flowable-task"
    if (node.type.endsWith("SubflowGraphTask")) return "primary"
    if (node.branchType == BranchType.ERROR) return "danger"
    if (node.branchType == BranchType.FINALLY) return "warning"
    if (collapsed.has(node.uid)) return "blue"
    return "default"
}

export function haveAdd(
    edge: GraphEdge,
    nodeByUid: Record<string, MinimalNode>,
    clustersRootTaskUids: string[],
    readOnlyUidPrefixes: string[],
) {
    if (
        readOnlyUidPrefixes.some(
            (prefix) => edge.source.startsWith(prefix) && edge.target.startsWith(prefix),
        )
    ) {
        return undefined
    }
    if (clustersRootTaskUids.includes(edge.target)) return undefined
    if (
        edge.source.startsWith(TRIGGERS_NODE_UID) ||
        edge.target.startsWith(TRIGGERS_NODE_UID)
    ) {
        return undefined
    }

    const dotSplitTarget = edge.target.split(".")
    dotSplitTarget.pop()
    const targetNodeClusterUid = dotSplitTarget.join(".")
    const clusterRootTaskId = Utils.afterLastDot(targetNodeClusterUid)
    const targetNode = nodeByUid[edge.target]

    if (
        targetNode.type.endsWith("GraphClusterEnd") &&
        nodeByUid[targetNodeClusterUid]?.task?.type?.endsWith("Parallel")
    ) {
        return undefined
    }
    if (targetNode.type.endsWith("GraphClusterRoot")) {
        return [clusterRootTaskId, "before"]
    }
    const sourceIsEndOfCluster = nodeByUid[edge.source].type.endsWith("GraphClusterEnd")
    if (!sourceIsEndOfCluster && targetNode.type.endsWith("GraphClusterEnd")) {
        return [Utils.afterLastDot(edge.source), "after"]
    }
    if (sourceIsEndOfCluster) {
        const dotSplitSource = edge.source.split(".")
        return [dotSplitSource[dotSplitSource.length - 2], "after"]
    }
    return [Utils.afterLastDot(edge.target), "before"]
}

export function getEdgeColor(
    edge: GraphEdge,
    nodeByUid: Record<string, MinimalNode>,
    clusterByNodeUid: Record<string, Cluster>,
) {
    const findRootBranchType = (nodeId: string): BranchType | null => {
        const uidParts = nodeId.split(".")
        for (let i = 1; i <= uidParts.length; i++) {
            const parentUid = uidParts.slice(0, i).join(".")
            const branchType = clusterByNodeUid[parentUid]?.branchType
            if (branchType) return branchType
        }
        return nodeByUid[nodeId]?.branchType ?? null
    }

    const sourceBranchType = findRootBranchType(edge.source)
    const targetBranchType = findRootBranchType(edge.target)

    return [sourceBranchType, targetBranchType].includes(BranchType.ERROR)
        ? "danger"
        : [sourceBranchType, targetBranchType].includes(BranchType.FINALLY)
          ? "warning"
          : null
}

export function getEdgeColorToken(edgeColor: string | null): string {
    switch (edgeColor) {
        case "danger":
            return "var(--ks-border-error)"
        case "warning":
            return "var(--ks-status-warning)"
        default:
            return "var(--ks-topology-dash)"
    }
}

export function generateGraph(
    _vueFlowId: string,
    flowId: string | undefined,
    namespace: string | undefined,
    flowGraph: FlowGraph | undefined,
    flowSource: string | undefined,
    hiddenNodes: string[],
    isHorizontal: boolean,
    edgeReplacer: EdgeReplacer,
    collapsed: Set<string>,
    clusterToNode: MinimalNode[],
    isReadOnly: boolean,
    isAllowedEdit: boolean,
    enableSubflowInteraction: boolean,
    getNodeDimensions: (
        node: MinimalNode,
        widthFn: (node: MinimalNode) => number,
        heightFn: (node: MinimalNode) => number,
    ) => {width: number; height: number} = (node, widthFn, heightFn) => ({
        width: widthFn(node),
        height: heightFn(node),
    }),
    animated: boolean = true,
): Elements | undefined {
    const elements: Elements = []

    if (!flowGraph || (flowSource && !flowHaveTasks(flowSource))) {
        console.warn("No flow graph or tasks found")
        elements.push({
            id: "start",
            type: "dot",
            position: {x: 0, y: 0},
            style: {width: "5px", height: "5px"},
            sourcePosition: isHorizontal ? Position.Right : Position.Bottom,
            targetPosition: isHorizontal ? Position.Left : Position.Top,
            parentNode: undefined,
            draggable: false,
        })
        elements.push({
            id: "end",
            type: "dot",
            position: isHorizontal ? {x: 50, y: 0} : {x: 0, y: 50},
            style: {width: "5px", height: "5px"},
            sourcePosition: isHorizontal ? Position.Right : Position.Bottom,
            targetPosition: isHorizontal ? Position.Left : Position.Top,
            parentNode: undefined,
            draggable: false,
        })
        elements.push({
            id: "start|end",
            source: "start",
            target: "end",
            type: "edge",
            data: {
                edge: {relation: {relationType: "SEQUENTIAL"}},
                isFlowable: false,
                initTask: true,
                color: "primary",
            },
        })
        return
    }

    const dagreGraph = generateDagreGraph(
        flowGraph,
        hiddenNodes,
        isHorizontal,
        edgeReplacer,
        collapsed,
        clusterToNode,
        getNodeDimensions,
    )

    const clusterByNodeUid: Record<string, Cluster> = {}
    const clusters = flowGraph.clusters || []
    const rawClusters = clusters.map((c) => c.cluster)
    const readOnlyUidPrefixes = rawClusters
        .filter((c) => c.type.endsWith("SubflowGraphCluster"))
        .map((c) => c.taskNode.uid)

    const nodeByUid = Object.fromEntries(
        flowGraph.nodes.concat(clusterToNode).map((node) => [node.uid, node]),
    )

    for (const cluster of clusters) {
        if (!edgeReplacer[cluster.cluster.uid] && !collapsed.has(cluster.cluster.uid)) {
            if (
                cluster.cluster.taskNode?.task?.type === "io.kestra.core.tasks.flows.Dag"
            ) {
                readOnlyUidPrefixes.push(cluster.cluster.taskNode.uid)
            }
            for (const nodeUid of cluster.nodes) {
                clusterByNodeUid[nodeUid] = cluster.cluster
            }

            const clusterUid = cluster.cluster.uid
            const dagreNode = dagreGraph.node(clusterUid)
            const parentNode = cluster.parents
                ? cluster.parents[cluster.parents.length - 1]
                : undefined
            const clusterColor = computeClusterColor(cluster.cluster)

            elements.push({
                id: clusterUid,
                type: "cluster",
                parentNode: parentNode,
                position: getNodePosition(
                    dagreNode,
                    parentNode ? dagreGraph.node(parentNode) : undefined,
                ),
                style: {
                    width:
                        clusterUid === TRIGGERS_NODE_UID && isHorizontal
                            ? NODE_SIZES.TRIGGER_CLUSTER_WIDTH + "px"
                            : dagreNode.width + "px",
                    height:
                        clusterUid === TRIGGERS_NODE_UID && !isHorizontal
                            ? NODE_SIZES.TRIGGER_CLUSTER_HEIGHT + "px"
                            : dagreNode.height + "px",
                    borderRadius: "var(--ks-radius-base)",
                    padding: "0.5rem",
                },
                data: {
                    collaspsible: true,
                    color: clusterColor,
                    taskNode: cluster.cluster.taskNode,
                    unused: cluster.cluster.taskNode
                        ? nodeByUid[cluster.cluster.taskNode.uid].unused
                        : false,
                },
                class: `ks-topology-${clusterColor}-border`,
            } as any)
        }
    }

    for (const node of flowGraph.nodes.concat(clusterToNode)) {
        if (!hiddenNodes.includes(node.uid) || node.type === "collapsedcluster") {
            const dagreNode = dagreGraph.node(node.uid)
            let nodeType = "task"
            if (isClusterRootOrEnd(node)) {
                nodeType = "dot"
            } else if (node.type.endsWith("CustomGraphNode")) {
                nodeType = "custom"
            } else if (node.type.includes("GraphTrigger")) {
                nodeType = "trigger"
            } else if (node.type === "collapsedcluster") {
                nodeType = "collapsedcluster"
            }

            let color = nodeColor(node, collapsed)
            if (node.type === "collapsedcluster") {
                const clusterDef = rawClusters.find((c) => c.uid === CLUSTER_PREFIX + node.uid)
                if (clusterDef) color = computeClusterColor(clusterDef)
            }
            const isReadOnlyTask =
                isReadOnly ||
                node.task?.type?.includes("$") ||
                readOnlyUidPrefixes.some((prefix) => node.uid.startsWith(prefix + "."))

            const cluster = clusterByNodeUid[node.uid]
            const nodeDimensions = getNodeDimensions(node, getNodeWidth, getNodeHeight)

            elements.push({
                id: node.uid,
                type: nodeType,
                position: getNodePosition(
                    dagreNode,
                    cluster ? dagreGraph.node(cluster.uid) : undefined,
                ),
                style: {
                    width: nodeDimensions.width + "px",
                    height: nodeDimensions.height + "px",
                    ...(node.type === "collapsedcluster" ? {borderRadius: "var(--ks-radius-base)"} : {}),
                },
                sourcePosition: isHorizontal ? Position.Right : Position.Bottom,
                targetPosition: isHorizontal ? Position.Left : Position.Top,
                parentNode: cluster ? cluster.uid : undefined,
                draggable: nodeType === "task" ? !isReadOnlyTask : false,
                data: {
                    node: node,
                    parent: cluster ? cluster : undefined,
                    namespace: cluster?.taskNode?.task?.namespace ?? namespace,
                    flowId: cluster?.taskNode?.task?.flowId ?? flowId,
                    isFlowable:
                        cluster?.uid === CLUSTER_PREFIX + node.uid &&
                        !node.type.endsWith("SubflowGraphTask"),
                    color,
                    expandable: isExpandableTask(node, clusterByNodeUid, edgeReplacer, enableSubflowInteraction),
                    isReadOnly: isReadOnlyTask,
                    executionId: node.executionId,
                    unused: node.unused,
                },
                class: node.type === "collapsedcluster" ? `ks-topology-${color}-border` : "",
            })
        }
    }

    const clusterRootTaskNodeUids = rawClusters.filter((c) => c.taskNode).map((c) => c.taskNode.uid)
    const edges = flowGraph.edges ?? []

    for (const edge of edges) {
        const newEdge = replaceIfCollapsed(edge.source, edge.target, edgeReplacer, hiddenNodes, collapsed)
        if (newEdge) {
            const edgeColor = getEdgeColor(edge, nodeByUid, clusterByNodeUid)
            const targetNodeType = nodeByUid[newEdge.target]?.type ?? ""
            const sourceNodeType = nodeByUid[newEdge.source]?.type ?? ""
            let edgeBoundary: "top" | "bottom" | undefined = undefined
            if (typeof targetNodeType === "string" && targetNodeType.endsWith("GraphClusterRoot")) {
                edgeBoundary = "top"
            } else if (
                typeof sourceNodeType === "string" &&
                sourceNodeType.endsWith("GraphClusterEnd")
            ) {
                edgeBoundary = "bottom"
            }
            elements.push({
                id: newEdge.source + "|" + newEdge.target,
                source: newEdge.source,
                target: newEdge.target,
                type: "edge",
                markerEnd: isClusterRootOrEnd(nodeByUid[newEdge.target])
                    ? ""
                    : {
                          id:
                              "marker-" +
                              (nodeByUid[newEdge.target].branchType
                                  ? nodeByUid[newEdge.target].branchType?.toLocaleLowerCase()
                                  : "custom"),
                          type: MarkerType.ArrowClosed,
                          color: getEdgeColorToken(edgeColor),
                      },
                data: {
                    haveAdd:
                        !isReadOnly &&
                        isAllowedEdit &&
                        haveAdd(edge, nodeByUid, clusterRootTaskNodeUids, readOnlyUidPrefixes),
                    edgeBoundary: edgeBoundary,
                    haveDashArray:
                        nodeByUid[edge.source].type.endsWith("GraphTrigger") ||
                        nodeByUid[edge.target].type.endsWith("GraphTrigger") ||
                        edge.source.startsWith(TRIGGERS_NODE_UID),
                    color: edgeColor,
                    unused: (edge as any).unused,
                },
                style: {zIndex: 10},
                animated: animated,
            })
        }
    }

    return elements
}

export function isClusterRootOrEnd(node: MinimalNode) {
    return [
        "GraphClusterRoot",
        "GraphClusterFinally",
        "GraphClusterAfterExecution",
        "GraphClusterEnd",
    ].some((s) => node.type.endsWith(s))
}

export function computeClusterColor(cluster: Cluster) {
    if (cluster.uid === CLUSTER_PREFIX + TRIGGERS_NODE_UID) return "triggers"
    if (cluster.type.endsWith("SubflowGraphCluster")) return "subflow"
    if (cluster.branchType === BranchType.ERROR) return "errors"
    return "flowable-task"
}

export function isExpandableTask(
    node: MinimalNode,
    clusterByNodeUid: Record<string, Cluster>,
    edgeReplacer: EdgeReplacer,
    enableSubflowInteraction?: boolean,
) {
    if (Object.values(edgeReplacer).includes(node.uid)) return true
    if (isCollapsedCluster(node)) return true
    return (
        node.type.endsWith("SubflowGraphTask") &&
        clusterByNodeUid[node.uid]?.uid?.replace(CLUSTER_PREFIX, "") !== node.uid &&
        enableSubflowInteraction
    )
}

export function getRootNodes(graph: FlowGraph) {
    const nodeUIDs = graph.nodes.map((node) => node.uid)
    const rootUIDs = nodeUIDs.filter((uid) => {
        return !graph.edges.some((edge) => edge.target === uid)
    })
    return graph.nodes.filter((node) => rootUIDs.includes(node.uid))
}

export function getTargetNodesEdges(graph: FlowGraph, nodeUid?: string) {
    if (!nodeUid) return undefined
    return graph.edges.filter((edge) => edge.source === nodeUid && edge.target)
}

export function getNextTaskNodes(graph: FlowGraph, initialNode: MinimalNode) {
    let edges: GraphEdge[],
        nextTaskNodes: MinimalNode[],
        nodeUIDs: string[] = [initialNode.uid]
    do {
        edges = nodeUIDs
            .flatMap((uid) => getTargetNodesEdges(graph, uid))
            .filter(Boolean) as GraphEdge[]
        if (edges.length === 0) return []
        nodeUIDs = edges.map((edge) => edge.target)
        nextTaskNodes = graph.nodes.filter((node) => nodeUIDs.includes(node.uid) && node.task)
    } while (!nextTaskNodes.length)
    return nextTaskNodes
}

export function areTasksIdenticalInGraphUntilTask(
    previousGraph: FlowGraph,
    currentGraph: FlowGraph,
    taskId?: string,
) {
    if (!taskId) return false

    let previousRootTaskNodes = getRootNodes(previousGraph)
    let currentRootTaskNodes = getRootNodes(currentGraph)

    if (previousRootTaskNodes.length !== currentRootTaskNodes.length) return false

    let failIndex = 120

    do {
        currentRootTaskNodes = currentRootTaskNodes.flatMap((node) =>
            getNextTaskNodes(currentGraph, node),
        )
        if (currentRootTaskNodes.some((node: any) => node.task.id === taskId)) return true

        previousRootTaskNodes = previousRootTaskNodes.flatMap((node) =>
            getNextTaskNodes(previousGraph, node),
        )
        if (previousRootTaskNodes.length !== currentRootTaskNodes.length) return false

        for (const currentTaskNode of currentRootTaskNodes) {
            const prevTaskNode = previousRootTaskNodes.find(
                (taskNode) => taskNode.task?.id === currentTaskNode.task?.id,
            )
            const prevTaskValue = (prevTaskNode?.task as Record<string, any>) ?? {}
            const currentTaskValue = (currentTaskNode.task as Record<string, any>) ?? {}
            if (
                !prevTaskNode ||
                Object.keys(prevTaskValue).length !== Object.keys(currentTaskValue).length
            ) {
                return false
            }
            if (!isEqual(prevTaskValue, currentTaskValue)) return false
        }
    } while (previousRootTaskNodes.length && currentRootTaskNodes.length && failIndex-- > 0)

    if (failIndex <= 0) {
        console.warn("areTasksIdenticalInGraphUntilTask: Infinite loop detected, stopping comparison.")
        return false
    }

    return true
}
