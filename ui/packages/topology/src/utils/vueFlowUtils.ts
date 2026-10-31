import {MarkerType, Position, useVueFlow} from "@vue-flow/core"
import type {GraphNode, GraphEdge, Elements} from "@vue-flow/core"
import * as dagre from "dagre"
import * as Utils from "./utils"
import {
    CLUSTER_PREFIX,
    DAGRE_NODE_SEP,
    DAGRE_RANK_SEP,
    LOOP_RETURN_HEAD_GAP,
    LOOP_RETURN_INSET,
    NODE_SIZES,
    SMOOTH_STEP_OFFSET,
    SMOOTH_STEP_RADIUS,
} from "./constants"
import {isDeepEqual} from "@kestra-io/design-system"

const TRIGGERS_NODE_UID = "root.Triggers"
// Never a real backend uid — a flow-level `errors:` section has no cluster of its own (unlike
// `Triggers`, which the backend already synthesizes), so the frontend synthesizes this one to
// give it a labelled lane instead of a floating dashed line (kestra-io/kestra#19666).
const ERRORS_NODE_UID = "root.Errors"

export enum BranchType {
    ERROR = "ERROR",
    FINALLY = "FINALLY",
    AFTER_EXECUTION = "AFTER_EXECUTION",
}

export interface GraphTaskRun {
    id: string;
    taskId: string;
    parentTaskRunId?: string;
    state: {
        current: string;
        duration?: string | null;
        histories?: {date: string; state: string}[];
    };
    outputs?: {executionId?: string} & Record<string, unknown>;
    attempts?: unknown[];
    value?: string;
}

/** Only what the graph reads off an execution; the package has no `@kestra-io/kestra-sdk` dependency. */
export interface GraphExecution {
    id?: string;
    state?: {current?: string};
    taskRunList?: GraphTaskRun[];
}

export interface MinimalNode {
    unused?: boolean;
    isFlowableLane?: boolean;
    executionId?: string;
    branchType?: BranchType;
    uid: string;
    type: string;
    disabled?: boolean;
    task?: {
        id?: string;
        type: string;
        namespace?: string;
        flowId?: string;
        disabled?: boolean;
    };
}

export interface Cluster {
    uid: string;
    type: string;
    taskNode: {
        uid: string;
        task: {
            type: string;
            namespace?: string;
            flowId?: string;
        } & Record<string, unknown>;
    } | null;
    // The backend only sets this on a node inside an error/finally branch, as MinimalNode already reflects.
    branchType?: BranchType;
}

interface FlowGraphEdge {
    source: string;
    target: string;
    relation?: {
        relationType?: string;
        value?: string;
    };
    unused?: boolean;
}

export interface FlowGraph {
    nodes: MinimalNode[];
    clusters: {
        cluster: Cluster;
        nodes: string[];
        parents: string[];
        start: string;
        end: string;
    }[];
    edges: FlowGraphEdge[];
}

export type FlowGraphCluster = FlowGraph["clusters"][number];

type EdgeReplacer = Record<string, string>;

export type NodeDimensionsFn = (
    node: MinimalNode,
    widthFn: (node: MinimalNode) => number,
    heightFn: (node: MinimalNode) => number,
) => {width: number; height: number}

// A loop's return edge closes a cycle, so these walks follow the flow's own direction only.
function forwardEdges(vueFlowId: string): GraphEdge[] {
    return useVueFlow(vueFlowId).getEdges.value.filter((edge) => !edge.data?.loopReturn)
}

export function predecessorsEdge(vueFlowId: string, nodeUid: string): GraphEdge[] {
    const nodes = []
    for (const edge of forwardEdges(vueFlowId)) {
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
    const nodes = []
    for (const edge of forwardEdges(vueFlowId)) {
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
    const {findNode} = useVueFlow(vueFlowId)
    const foundNode = findNode(nodeUid)
    const nodes: (GraphEdge | GraphNode)[] = foundNode ? [foundNode] : []
    for (const edge of forwardEdges(vueFlowId)) {
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
    const {findNode} = useVueFlow(vueFlowId)
    const nodes = [findNode(nodeUid)]
    for (const edge of forwardEdges(vueFlowId)) {
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
    flowGraph: FlowGraph,
    hiddenNodes: string[],
    isHorizontal: boolean,
    edgeReplacer: EdgeReplacer,
    collapsed: Set<string>,
    clusterToNode: MinimalNode[],
    getNodeDimensions: NodeDimensionsFn = (node, widthFn, heightFn) => ({
        width: widthFn(node),
        height: heightFn(node),
    }),
    laneStartUids: Set<string> = new Set(),
) {
    const dagreGraph = new dagre.graphlib.Graph({compound: true})
    dagreGraph.setDefaultEdgeLabel(() => ({}))
    // Vertically, a lane's own first node carries the header's height, so dagre grows the cluster
    // around it and still leaves a full rank apart from whatever sits above. Horizontally the
    // header is across the flow, where no node can carry it: `insertLaneHeaderBands` makes room after layout.
    dagreGraph.setGraph({
        rankdir: isHorizontal ? "LR" : "TB",
        ranksep: DAGRE_RANK_SEP,
        ...(isHorizontal && laneStartUids.size
            ? {nodesep: DAGRE_NODE_SEP + NODE_SIZES.LANE_HEADER_HEIGHT}
            : {}),
    })

    for (const node of flowGraph.nodes) {
        if (!hiddenNodes.includes(node.uid)) {
            const dimensions = getNodeDimensions(node, getNodeWidth, getNodeHeight)
            if (!isHorizontal && laneStartUids.has(node.uid)) {
                dimensions.height += NODE_SIZES.LANE_HEADER_HEIGHT
            }
            dagreGraph.setNode(node.uid, dimensions)
        }
    }

    for (const cluster of flowGraph.clusters || []) {
        const nodeUid = cluster.cluster.uid.replace(CLUSTER_PREFIX, "")
        const absorbedByParent = edgeReplacer[cluster.cluster.uid] && edgeReplacer[cluster.cluster.uid] !== nodeUid

        if (collapsed.has(nodeUid) && !absorbedByParent) {
            const flowableNode = flowGraph.nodes.find((n) => n.uid === nodeUid)
            const node = {
                uid: nodeUid,
                type: "collapsedcluster",
                isFlowableLane: isTrueFlowableCluster(cluster.cluster),
                executionId: flowableNode?.executionId,
            }
            const dimensions = getNodeDimensions(node, getNodeWidth, getNodeHeight)
            dagreGraph.setNode(nodeUid, dimensions)
            clusterToNode.push(node)
        } else if (!edgeReplacer[cluster.cluster.uid]) {
            dagreGraph.setNode(cluster.cluster.uid, {clusterLabelPos: "top"})
            for (const node of cluster.nodes || []) {
                if (!hiddenNodes.includes(node)) {
                    dagreGraph.setParent(node, cluster.cluster.uid)
                }
            }
        }
        if (cluster.parents && !absorbedByParent) {
            const nodeChild = edgeReplacer[cluster.cluster.uid]
                ? edgeReplacer[cluster.cluster.uid]
                : cluster.cluster.uid
            if (!hiddenNodes.includes(nodeChild) || collapsed.has(nodeChild)) {
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
    if (isHorizontal) {
        insertLaneHeaderBands(dagreGraph)
    }
    return dagreGraph
}

function insertLaneHeaderBands(dagreGraph: dagre.graphlib.Graph) {
    const boxOf = (uid: string) => {
        const node = dagreGraph.node(uid)
        return {top: node.y - node.height / 2, bottom: node.y + node.height / 2}
    }
    const ancestorsOf = (uid: string) => {
        const ancestors: string[] = []
        for (let parent = dagreGraph.parent(uid); parent; parent = dagreGraph.parent(parent)) {
            ancestors.push(parent)
        }
        return ancestors
    }
    // @types/dagre declares children() as a single string; graphlib returns an array.
    const childrenOf = (uid: string) => (dagreGraph.children(uid) as unknown as string[] | undefined) ?? []
    const descendantsOf = (uid: string): string[] =>
        childrenOf(uid).flatMap((child) => [child, ...descendantsOf(child)])
    const siblingsOf = (uid: string) => {
        const parent = dagreGraph.parent(uid)
        const siblings = parent ? childrenOf(parent) : dagreGraph.nodes().filter((each) => !dagreGraph.parent(each))
        return siblings.filter((each) => each !== uid)
    }

    for (const lane of dagreGraph.nodes().filter((uid) => childrenOf(uid).length)) {
        const growing = [lane, ...ancestorsOf(lane)]
        const moved = new Set(descendantsOf(lane))
        for (const box of growing) {
            const bottom = boxOf(box).bottom
            for (const sibling of siblingsOf(box).filter((each) => boxOf(each).top >= bottom)) {
                for (const each of [sibling, ...descendantsOf(sibling)]) {
                    moved.add(each)
                }
            }
        }

        for (const uid of moved) {
            dagreGraph.node(uid).y += NODE_SIZES.LANE_HEADER_HEIGHT
        }
        for (const uid of growing) {
            dagreGraph.node(uid).y += NODE_SIZES.LANE_HEADER_HEIGHT / 2
            dagreGraph.node(uid).height += NODE_SIZES.LANE_HEADER_HEIGHT
        }
    }
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

/** The dimensions dagre lays a node out at. Deliberately has no zoom/LOD parameter: only
 *  `execution` presence (entering/leaving an execution, already its own graph regeneration
 *  point) ever changes what this returns — crossing a zoom threshold never does
 *  (kestra-io/kestra#19666). */
export function buildEffectiveGetNodeDimensions(
    hasExecution: boolean,
    override?: (
        node: MinimalNode,
        widthFn: (node: MinimalNode) => number,
        heightFn: (node: MinimalNode) => number,
    ) => {width: number; height: number},
) {
    return (
        node: MinimalNode,
        getNodeWidthFn: (node: MinimalNode) => number,
        getNodeHeightFn: (node: MinimalNode) => number,
    ) => {
        const dimensions = override
            ? override(node, getNodeWidthFn, getNodeHeightFn)
            : {width: getNodeWidthFn(node), height: getNodeHeightFn(node)}

        if (hasExecution && (isTaskNode(node) || isTriggerNode(node) || isCustomNode(node) || isCollapsedLane(node))) {
            dimensions.width = NODE_SIZES.TASK_WIDTH_EXECUTION
        }

        if (hasExecution && (isTaskNode(node) || isCollapsedLane(node))) {
            dimensions.height = NODE_SIZES.TASK_HEIGHT_EXECUTION
        }

        return dimensions
    }
}

export function getNodeWidth(node: MinimalNode) {
    return isTriggerNode(node)
        ? NODE_SIZES.TRIGGER_WIDTH
        : isTaskNode(node) || isCustomNode(node)
          ? NODE_SIZES.TASK_WIDTH
          : isCollapsedLane(node)
            ? NODE_SIZES.TASK_WIDTH
            : isCollapsedCluster(node)
              ? NODE_SIZES.COLLAPSED_CLUSTER_WIDTH
              : NODE_SIZES.DOT_WIDTH
}

export function getNodeHeight(node: MinimalNode) {
    return isTriggerNode(node)
        ? NODE_SIZES.TRIGGER_HEIGHT
        : isTaskNode(node)
          ? NODE_SIZES.TASK_HEIGHT
          : isCollapsedLane(node)
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

export function isCollapsedLane(node: MinimalNode) {
    return isCollapsedCluster(node) && Boolean(node.isFlowableLane)
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

    const lines = source.split(/\r?\n/)

    // Phase 1: locate the root-level `tasks:` block — its start line, and its
    // end, i.e. the next root-level key (a line starting with a non-space
    // character) or the end of the source. Single pass, bounded, no backtracking.
    let tasksLineIndex = -1
    let blockEndIndex = lines.length
    for (let i = 0; i < lines.length; i++) {
        if (tasksLineIndex === -1) {
            if (/^tasks\s*:\s*$/.test(lines[i])) {
                tasksLineIndex = i
            }
        } else if (/^\S/.test(lines[i])) {
            blockEndIndex = i
            break
        }
    }
    if (tasksLineIndex === -1) return false

    // Phase 2: within that block, a task item starts with "- " (dash-space) and
    // must contain at least one `id:`/`id :` line — either right after the dash
    // on the same line, or on a line below it.
    let sawTaskItem = false
    for (let i = tasksLineIndex + 1; i < blockEndIndex; i++) {
        const line = lines[i]

        const dashMatch = /^\s+-\s*(.*)$/.exec(line)
        if (dashMatch) {
            sawTaskItem = true
            if (/^id\s*:/.test(dashMatch[1])) {
                return true
            }
        } else if (sawTaskItem && /^\s*id\s*:/.test(line)) {
            return true
        }
    }
    return false
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

function isLoopTask(type?: string): boolean {
    return Boolean(type?.endsWith(".LoopUntil") || type?.endsWith(".Loop"))
}

/** A flowable's own gate node (e.g. the `Parallel` task itself) is a real graph node — edges fan
 *  out of it or chain through it — but it must render once, as its cluster's lane header, not
 *  again as a duplicate task box inside that same cluster (kestra-io/kestra#19666). Excludes
 *  `Triggers` (no lane header) and subflow clusters (still their own expandable task node). */
export function isTrueFlowableCluster(cluster: Cluster): boolean {
    return Boolean(cluster.taskNode)
        && !cluster.type.endsWith("SubflowGraphCluster")
        && cluster.uid !== CLUSTER_PREFIX + TRIGGERS_NODE_UID
}

/** Maps each flowable's gate node onto its lane's own root dot, so the gate — already drawn as
 *  the lane header — stops rendering as a second blank dot in the same gap. A collapsed lane is
 *  excluded: there that uid is what the "collapsedcluster" placeholder renders as. */
export function getFlowableGateAliases(
    clusters: FlowGraph["clusters"],
    collapsed: Set<string> = new Set(),
    edgeReplacer: EdgeReplacer = {},
): Record<string, string> {
    const aliases: Record<string, string> = {}
    for (const entry of clusters) {
        const gateUid = entry.cluster.taskNode?.uid
        if (!gateUid || !entry.start || !isTrueFlowableCluster(entry.cluster)) continue
        if (collapsed.has(gateUid) || edgeReplacer[entry.cluster.uid]) continue
        aliases[gateUid] = entry.start
    }
    return aliases
}

/** Maps the flow's own root dot onto the Triggers lane's end dot the backend wires it straight out
 *  of, so the gap between the trigger box and the first task stops rendering two joints in a row
 *  (kestra-io/kestra#21047). The lane's end is the one that stays, because it is a member of the
 *  lane: keeping it leaves the box's own layout untouched — its height, its padding, and the point
 *  several triggers fan into all stay where they were — and only the joint outside the box goes.
 *  The flow root is kept whenever anything else meets there, since `errors:` and `finally:` fan out
 *  of it and it is then a junction rather than a redundant joint. A collapsed or absorbed lane is
 *  excluded, since there the placeholder already stands for both. */
export function getTriggersJoinAlias(
    clusters: FlowGraph["clusters"],
    nodes: MinimalNode[],
    edges: FlowGraphEdge[],
    collapsed: Set<string> = new Set(),
    edgeReplacer: EdgeReplacer = {},
): Record<string, string> {
    const triggers = clusters.find((entry) => entry.cluster.uid === CLUSTER_PREFIX + TRIGGERS_NODE_UID)
    if (!triggers?.end || collapsed.has(TRIGGERS_NODE_UID)) return {}
    if (edgeReplacer[triggers.cluster.uid] || edgeReplacer[triggers.end]) return {}

    const flowRoot = edges.find(
        (edge) =>
            edge.source === triggers.end &&
            nodes.find((node) => node.uid === edge.target)?.type.endsWith("GraphClusterRoot"),
    )?.target
    if (!flowRoot) return {}

    // The lane's end feeds it and the first task leaves it; a third edge means something branches
    // there, so the dot carries meaning and stays.
    const touching = edges.filter((edge) => edge.source === flowRoot || edge.target === flowRoot)
    if (touching.length > 2) return {}

    return {[flowRoot]: triggers.end}
}

/** The task ids of a lane's direct children — real tasks and nested lanes alike, each counted
 *  once — for the lane header's child count and aggregate state. Excludes the lane's own gate
 *  node and the structural root/end/finally/afterExecution markers. */
export function getClusterChildTaskIds(
    clusterEntry: {cluster: Cluster; nodes: string[]},
    nodeByUid: Record<string, MinimalNode>,
): string[] {
    const taskNodeUid = clusterEntry.cluster.taskNode?.uid
    return clusterEntry.nodes
        .filter((uid) => {
            if (uid === taskNodeUid) return false
            const node = nodeByUid[uid]
            // A nested lane's own uid (`cluster_...`) never has an entry in `nodeByUid`: it is a
            // real child, just not a plain task.
            return !node || !isClusterRootOrEnd(node)
        })
        .map((uid) => Utils.afterLastDot(uid.replace(CLUSTER_PREFIX, "")) ?? uid)
}

export interface AddTaskTarget {
    refId: string
    position: "before" | "after"
    /** Both ends of the edge, when it is a `dependsOn` relation inside a Dag. */
    dagDependency?: {fromId?: string; toId?: string}
}

export function haveAdd(
    edge: FlowGraphEdge,
    nodeByUid: Record<string, MinimalNode>,
    clustersRootTaskUids: string[],
    readOnlyUidPrefixes: string[],
): AddTaskTarget | undefined {
    if (
        readOnlyUidPrefixes.some(
            (prefix) => edge.source.startsWith(prefix) && edge.target.startsWith(prefix),
        )
    ) {
        return undefined
    }
    if (clustersRootTaskUids.includes(edge.target)) return undefined
    // A trigger is not a task, so nothing can be inserted on the lane's own wiring. The run that
    // leaves the lane for a real task is the exception: once the flow root is folded onto the lane
    // end, that run is where a task is inserted before the first one (kestra-io/kestra#21047).
    if (edge.target.startsWith(TRIGGERS_NODE_UID)) return undefined
    if (edge.source.startsWith(TRIGGERS_NODE_UID) && isClusterRootOrEnd(nodeByUid[edge.target])) {
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
    // A Dag orders its tasks through `dependsOn`, so an insertion there has to know both ends of
    // the edge it landed on, not just the neighbour it is placed next to.
    const isDagLane = nodeByUid[targetNodeClusterUid]?.task?.type?.endsWith(".Dag") === true
    const dagTaskIdAt = (uid: string): string | undefined => {
        const node = nodeByUid[uid]
        if (!node) return undefined
        if (node.type.endsWith("GraphClusterRoot") || node.type.endsWith("GraphClusterEnd")) return undefined
        const parts = uid.split(".")
        parts.pop()
        return parts.join(".") === targetNodeClusterUid ? Utils.afterLastDot(uid) : undefined
    }
    const withDag = (target: AddTaskTarget): AddTaskTarget => {
        if (!isDagLane) return target
        const fromId = dagTaskIdAt(edge.source)
        const toId = dagTaskIdAt(edge.target)
        return fromId || toId ? {...target, dagDependency: {fromId, toId}} : target
    }

    if (targetNode.type.endsWith("GraphClusterRoot")) {
        return clusterRootTaskId ? {refId: clusterRootTaskId, position: "before"} : undefined
    }
    // The Triggers lane owns no task, so "after the lane's task" means nothing for it and the
    // insertion is placed from the edge's target instead.
    const sourceIsEndOfCluster = nodeByUid[edge.source].type.endsWith("GraphClusterEnd")
        && !edge.source.startsWith(TRIGGERS_NODE_UID)
    if (!sourceIsEndOfCluster && targetNode.type.endsWith("GraphClusterEnd")) {
        const refId = Utils.afterLastDot(edge.source)
        return refId ? withDag({refId, position: "after"}) : undefined
    }
    if (sourceIsEndOfCluster) {
        const dotSplitSource = edge.source.split(".")
        const refId = dotSplitSource[dotSplitSource.length - 2]
        return refId ? {refId, position: "after"} : undefined
    }
    const refId = Utils.afterLastDot(edge.target)
    return refId ? withDag({refId, position: "before"}) : undefined
}

export function getEdgeColor(
    edge: FlowGraphEdge,
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

/** A flow-level `errors:` task has no cluster of its own — unlike a nested flowable's own error
 *  branch, it hangs directly off the (invisible) root — so the frontend synthesizes one lane
 *  wrapping every such task, the same way the backend already synthesizes the `Triggers` cluster
 *  (kestra-io/kestra#19666). Returns `flowGraph.clusters` unchanged when there is nothing to wrap. */
export function withSyntheticErrorsLane(flowGraph: {nodes: MinimalNode[]; clusters?: FlowGraph["clusters"]}): FlowGraph["clusters"] {
    const clusters = flowGraph.clusters ?? []
    const clusteredUids = new Set(clusters.flatMap((c) => c.nodes))
    const rootErrorUids = flowGraph.nodes
        .filter((node) => node.branchType === BranchType.ERROR && !clusteredUids.has(node.uid))
        .map((node) => node.uid)

    if (!rootErrorUids.length) return clusters

    const errorsLane: FlowGraph["clusters"][number] = {
        cluster: {
            uid: CLUSTER_PREFIX + ERRORS_NODE_UID,
            type: "io.kestra.core.models.hierarchies.GraphCluster",
            taskNode: null,
            branchType: BranchType.ERROR,
        },
        nodes: rootErrorUids,
        parents: [],
        start: rootErrorUids[0],
        end: rootErrorUids[rootErrorUids.length - 1],
    }

    return [...clusters, errorsLane]
}

/** A Parallel fans out to siblings whose order carries no meaning, so one add button on the split
 *  point says "add a branch" where one per branch reads as "insert before this one". Returns, per
 *  fanning-out source, the single target whose edge keeps the button. Ordered fan-outs — a Switch's
 *  cases, a Dag's dependencies — keep one button each, since there the position is the meaning. */
export function pickFanOutAddEdges(
    edges: FlowGraphEdge[],
    clusterByNodeUid: Record<string, Cluster>,
): Map<string, string> {
    const targetsBySource = new Map<string, string[]>()
    for (const edge of edges) {
        const lane = clusterByNodeUid[edge.source]
        if (!lane?.taskNode?.task?.type?.endsWith("Parallel")) continue
        targetsBySource.set(edge.source, (targetsBySource.get(edge.source) ?? []).concat(edge.target))
    }

    const owners = new Map<string, string>()
    for (const [source, targets] of targetsBySource) {
        if (targets.length > 1) owners.set(source, targets[0])
    }
    return owners
}

export interface EdgeTurnOptions {
    /** How far each end sits inside the lane it crosses out of / into. */
    gap?: {leaving: number; entering: number};
    /** An error branch keeps its long run out of the main column: turn level with the end that
     *  stays in it — `"source"` entering the branch, `"target"` leaving it. */
    bypass?: "source" | "target";
    offset?: number;
}

/** Where a stepped edge should turn, along the axis the graph flows in. `undefined` leaves the
 *  turn to vue-flow's own midpoint. Both ends are measured from the node, not from the lane border
 *  it crossed, so a plain midpoint sits off-centre in the gap a reader actually sees. */
export function edgeTurnPosition(
    from: number,
    to: number,
    {gap, bypass, offset = SMOOTH_STEP_OFFSET}: EdgeTurnOptions,
): number | undefined {
    if (!gap && !bypass) return undefined

    const low = Math.min(from, to) + offset
    const high = Math.max(from, to) - offset
    if (low > high) return undefined

    if (bypass) {
        const atSource = bypass === "source"
        const forward = from < to
        return atSource === forward ? low : high
    }
    if (!gap) return undefined

    const towards = Math.sign(to - from)
    const centre = ((from + towards * gap.leaving) + (to - towards * gap.entering)) / 2
    return Math.min(Math.max(centre, low), high)
}

/** A fan-out's single add button belongs on the run every branch still shares — between the lane's
 *  own marker and the split — rather than on the drop into whichever branch happens to carry it. */
export function fanOutSplitPosition(from: number, to: number, turn?: number): number {
    return (from + (turn ?? (from + to) / 2)) / 2
}

interface Point {
    x: number;
    y: number;
}

export interface LoopReturnBounds {
    right: number;
    bottom: number;
    entry: Point;
}

/** A loop lane's return run: out of its end marker, along the lane's borders, and back to its
 *  start marker, `entry`, across the flow so its head clears the edge coming into that marker. */
export function loopReturnPath(source: Point, {right, bottom, entry}: LoopReturnBounds, isHorizontal: boolean): string {
    const points = isHorizontal
        ? [source, {x: right, y: source.y}, {x: right, y: bottom}, {x: entry.x, y: bottom}, {x: entry.x, y: entry.y + LOOP_RETURN_HEAD_GAP}]
        : [source, {x: source.x, y: bottom}, {x: right, y: bottom}, {x: right, y: entry.y}, {x: entry.x + LOOP_RETURN_HEAD_GAP, y: entry.y}]
    return roundedPolyline(points, SMOOTH_STEP_RADIUS)
}

function roundedPolyline(points: Point[], radius: number): string {
    const length = (a: Point, b: Point) => Math.abs(a.x - b.x) + Math.abs(a.y - b.y)
    let d = `M${points[0].x},${points[0].y}`
    for (let i = 1; i < points.length - 1; i++) {
        const [before, corner, after] = [points[i - 1], points[i], points[i + 1]]
        const r = Math.min(radius, length(before, corner) / 2, length(corner, after) / 2)
        const towards = (p: Point) => ({
            x: corner.x + Math.sign(p.x - corner.x) * r,
            y: corner.y + Math.sign(p.y - corner.y) * r,
        })
        const [start, end] = [towards(before), towards(after)]
        d += ` L${start.x},${start.y} Q${corner.x},${corner.y} ${end.x},${end.y}`
    }
    const last = points[points.length - 1]
    return `${d} L${last.x},${last.y}`
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
    getNodeDimensions: NodeDimensionsFn = (node, widthFn, heightFn) => ({
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

    const clusters = withSyntheticErrorsLane(flowGraph)
    const nodeAliases = {
        ...getFlowableGateAliases(clusters, collapsed, edgeReplacer),
        ...getTriggersJoinAlias(clusters, flowGraph.nodes, flowGraph.edges ?? [], collapsed, edgeReplacer),
    }
    const hiddenOrAliased = Object.keys(nodeAliases).length
        ? hiddenNodes.concat(Object.keys(nodeAliases))
        : hiddenNodes
    const edges = (flowGraph.edges ?? [])
        .map((edge) => ({
            ...edge,
            source: nodeAliases[edge.source] ?? edge.source,
            target: nodeAliases[edge.target] ?? edge.target,
        }))
        .filter((edge) => edge.source !== edge.target)

    // `generateDagreGraph` already makes room for every header; vertically only the node carrying it renders lower.
    const laneStartUids = new Set(
        clusters
            .filter((c) => !edgeReplacer[c.cluster.uid] && !collapsed.has(c.cluster.uid.replace(CLUSTER_PREFIX, "")) && c.start)
            .map((c) => c.start),
    )

    const dagreGraph = generateDagreGraph(
        {...flowGraph, clusters, edges},
        hiddenOrAliased,
        isHorizontal,
        edgeReplacer,
        collapsed,
        clusterToNode,
        getNodeDimensions,
        laneStartUids,
    )

    const clusterByNodeUid: Record<string, Cluster> = {}
    const rawClusters = clusters.map((c) => c.cluster)
    const readOnlyUidPrefixes = rawClusters
        .filter((c) => c.type.endsWith("SubflowGraphCluster") && c.taskNode)
        .map((c) => c.taskNode!.uid)

    const nodeByUid = Object.fromEntries(
        flowGraph.nodes.concat(clusterToNode).map((node) => [node.uid, node]),
    )

    // How far a node sits from its lane's own borders, along the axis the graph flows in. An edge
    // crossing a border is drawn to the node, not to the border, so without these it turns level
    // with the border, and neither the turn nor the add button on it reads as being outside the
    // lane (kestra-io/kestra#19787).
    const laneSpan: Record<string, number> = {}
    const laneInset: Record<string, {before: number; after: number}> = {}

    for (const cluster of clusters) {
        const clusterNodeUid = cluster.cluster.uid.replace(CLUSTER_PREFIX, "")
        const absorbedByParent = edgeReplacer[cluster.cluster.uid] && edgeReplacer[cluster.cluster.uid] !== clusterNodeUid
        const parentClusterUid = cluster.parents?.[cluster.parents.length - 1]
        if (collapsed.has(clusterNodeUid) && !absorbedByParent && parentClusterUid) {
            const parentCluster = rawClusters.find((c) => c.uid === parentClusterUid)
            if (parentCluster) clusterByNodeUid[clusterNodeUid] = parentCluster
        }
        if (!edgeReplacer[cluster.cluster.uid] && !collapsed.has(clusterNodeUid)) {
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
            const childTaskIds = getClusterChildTaskIds(cluster, nodeByUid)

            const clusterPosition = getNodePosition(
                dagreNode,
                parentNode ? dagreGraph.node(parentNode) : undefined,
            )

            elements.push({
                id: clusterUid,
                type: "cluster",
                parentNode: parentNode,
                // Without this the cluster falls back to vue-flow's global `nodesDraggable`, and
                // dropping one on an edge emits a move for whatever its uid ends with.
                draggable: false,
                position: clusterPosition,
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
                    collaspsible: childTaskIds.length > 0,
                    color: clusterColor,
                    // The triggers box is the only place a trigger can be added from the canvas:
                    // triggers are not tasks, so no edge `+` ever targets them.
                    canAddTrigger:
                        clusterUid === CLUSTER_PREFIX + TRIGGERS_NODE_UID &&
                        Boolean(isAllowedEdit) &&
                        !isReadOnly,
                    taskNode: cluster.cluster.taskNode,
                    unused: cluster.cluster.taskNode
                        ? nodeByUid[cluster.cluster.taskNode.uid]?.unused
                        : false,
                    // A flowable renders once — as this lane's own header — only for a real
                    // Parallel/Sequential/If/… lane; `Triggers` and a subflow call keep their own
                    // node and menu.
                    isFlowableLane: isTrueFlowableCluster(cluster.cluster),
                    childTaskIds,
                    executionId: cluster.cluster.taskNode
                        ? nodeByUid[cluster.cluster.taskNode.uid]?.executionId
                        : undefined,
                    isReadOnly:
                        isReadOnly ||
                        Boolean(cluster.cluster.taskNode?.task?.type?.includes("$")) ||
                        readOnlyUidPrefixes.some((prefix) => cluster.cluster.taskNode?.uid.startsWith(prefix + ".")),
                },
                class: `ks-topology-${clusterColor}-border`,
            })

            laneSpan[clusterUid] = isHorizontal
                ? dagreNode.width
                : dagreNode.height
        }
    }

    for (const node of flowGraph.nodes.concat(clusterToNode)) {
        if (!hiddenOrAliased.includes(node.uid) || node.type === "collapsedcluster") {
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

            const collapsedEntry = node.type === "collapsedcluster"
                ? clusters.find((c) => c.cluster.uid === CLUSTER_PREFIX + node.uid)
                : undefined

            let color = nodeColor(node, collapsed)
            if (collapsedEntry) color = computeClusterColor(collapsedEntry.cluster)
            const isReadOnlyTask =
                isReadOnly ||
                node.task?.type?.includes("$") ||
                readOnlyUidPrefixes.some((prefix) => node.uid.startsWith(prefix + "."))

            const cluster = clusterByNodeUid[node.uid]
            const nodeDimensions = getNodeDimensions(node, getNodeWidth, getNodeHeight)

            const nodePosition = getNodePosition(
                dagreNode,
                cluster ? dagreGraph.node(cluster.uid) : undefined,
            )
            // Its dagre box is a header taller than the node; the node itself sits at the bottom
            // of it, leaving the header the space above.
            if (!isHorizontal && laneStartUids.has(node.uid)) {
                nodePosition.y += NODE_SIZES.LANE_HEADER_HEIGHT
            }

            if (cluster && laneSpan[cluster.uid] !== undefined) {
                const before = isHorizontal ? nodePosition.x : nodePosition.y
                const size = isHorizontal ? nodeDimensions.width : nodeDimensions.height
                laneInset[node.uid] = {before, after: laneSpan[cluster.uid] - before - size}
            }

            elements.push({
                id: node.uid,
                type: nodeType,
                position: nodePosition,
                style: {
                    width: nodeDimensions.width + "px",
                    height: nodeDimensions.height + "px",
                    ...(node.type === "collapsedcluster" ? {borderRadius: "var(--ks-radius-base)"} : {}),
                },
                sourcePosition: isHorizontal ? Position.Right : Position.Bottom,
                targetPosition: isHorizontal ? Position.Left : Position.Top,
                parentNode: cluster ? cluster.uid : undefined,
                // The drag is the browser's own (HTML5), like the No-code block cards, so vue-flow
                // must not also reposition the node under the pointer.
                draggable: false,
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
                    isMovable: Boolean(isAllowedEdit) && !isReadOnlyTask && isTaskNode(node),
                    // A collapsed lane still says what it is: the placeholder renders the very
                    // same header the expanded lane does.
                    ...(collapsedEntry
                        ? {
                            isFlowableLane: isTrueFlowableCluster(collapsedEntry.cluster),
                            taskNode: collapsedEntry.cluster.taskNode,
                            childTaskIds: getClusterChildTaskIds(collapsedEntry, nodeByUid),
                            executionId: collapsedEntry.cluster.taskNode
                                ? nodeByUid[collapsedEntry.cluster.taskNode.uid]?.executionId
                                : undefined,
                        }
                        : {}),
                },
                class: [
                    node.type === "collapsedcluster" ? `ks-topology-${color}-border` : "",
                    // vue-flow adds `nopan` to nodes it drags itself; it no longer does, and without
                    // it a drag starting on a card pans the canvas instead.
                    Boolean(isAllowedEdit) && !isReadOnlyTask && isTaskNode(node) ? "nopan" : "",
                ]
                    .filter(Boolean)
                    .join(" "),
            })
        }
    }

    const clusterRootTaskNodeUids = rawClusters.filter((c) => c.taskNode).map((c) => c.taskNode!.uid)
    const fanOutAddEdge = pickFanOutAddEdges(edges, clusterByNodeUid)

    for (const edge of edges) {
        const newEdge = replaceIfCollapsed(edge.source, edge.target, edgeReplacer, hiddenOrAliased, collapsed)
        if (newEdge) {
            const edgeColor = getEdgeColor(edge, nodeByUid, clusterByNodeUid)
            const sourceLane = clusterByNodeUid[newEdge.source]
            const targetLane = clusterByNodeUid[newEdge.target]
            const leaving = sourceLane && sourceLane !== targetLane ? laneInset[newEdge.source]?.after ?? 0 : 0
            const entering = targetLane && targetLane !== sourceLane ? laneInset[newEdge.target]?.before ?? 0 : 0
            // An error branch runs alongside the main flow, not through it: turning level with the
            // endpoint that stays in the main column keeps its long run in its own column, instead
            // of straight across whatever lane happens to sit between the two.
            const sourceIsError = sourceLane?.branchType === BranchType.ERROR
            const targetIsError = targetLane?.branchType === BranchType.ERROR
            const bypass = sourceIsError === targetIsError ? undefined : targetIsError ? "source" : "target"
            const fanOutOwner = fanOutAddEdge.get(newEdge.source)
            const fansOut = fanOutOwner !== undefined
            const carriesAdd = !fansOut || fanOutOwner === newEdge.target

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
                    fansOut,
                    haveAdd:
                        carriesAdd &&
                        !isReadOnly &&
                        isAllowedEdit &&
                        haveAdd(edge, nodeByUid, clusterRootTaskNodeUids, readOnlyUidPrefixes),
                    laneGap: leaving || entering ? {leaving, entering} : undefined,
                    bypass,
                    haveDashArray:
                        nodeByUid[edge.source].type.endsWith("GraphTrigger") ||
                        nodeByUid[edge.target].type.endsWith("GraphTrigger") ||
                        edge.source.startsWith(TRIGGERS_NODE_UID),
                    color: edgeColor,
                    unused: edge.unused,
                    value: edge.relation?.value,
                    relationType: edge.relation?.relationType,
                },
                style: {zIndex: 10},
                animated: animated,
            })
        }
    }

    for (const cluster of clusters) {
        const clusterUid = cluster.cluster.uid
        const taskNode = cluster.cluster.taskNode
        if (
            !isLoopTask(taskNode?.task?.type) ||
            edgeReplacer[clusterUid] ||
            collapsed.has(clusterUid.replace(CLUSTER_PREFIX, "")) ||
            hiddenOrAliased.includes(cluster.start) ||
            hiddenOrAliased.includes(cluster.end) ||
            !nodeByUid[cluster.start] ||
            !nodeByUid[cluster.end]
        ) {
            continue
        }

        const dagreNode = dagreGraph.node(clusterUid)
        const startNode = dagreGraph.node(cluster.start)
        if (!dagreNode || !startNode) continue

        elements.push({
            id: `loop-return|${cluster.end}|${cluster.start}`,
            source: cluster.end,
            target: cluster.start,
            type: "edge",
            markerEnd: {
                id: "marker-custom",
                type: MarkerType.ArrowClosed,
                color: getEdgeColorToken(null),
            },
            data: {
                loopReturn: {
                    right: dagreNode.x + dagreNode.width / 2 - LOOP_RETURN_INSET,
                    bottom: dagreNode.y + dagreNode.height / 2 - LOOP_RETURN_INSET,
                    // A vertical lane start sits at the bottom of a dagre box one header taller than it.
                    entry: {
                        x: startNode.x,
                        y: startNode.y + (!isHorizontal && laneStartUids.has(cluster.start) ? NODE_SIZES.LANE_HEADER_HEIGHT / 2 : 0),
                    },
                },
            },
            style: {zIndex: 10},
            animated: false,
        })
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
    let edges: FlowGraphEdge[],
        nextTaskNodes: MinimalNode[],
        nodeUIDs: string[] = [initialNode.uid]
    do {
        edges = nodeUIDs
            .flatMap((uid) => getTargetNodesEdges(graph, uid))
            .filter(Boolean) as FlowGraphEdge[]
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
        if (currentRootTaskNodes.some((node) => node.task?.id === taskId)) return true

        previousRootTaskNodes = previousRootTaskNodes.flatMap((node) =>
            getNextTaskNodes(previousGraph, node),
        )
        if (previousRootTaskNodes.length !== currentRootTaskNodes.length) return false

        for (const currentTaskNode of currentRootTaskNodes) {
            const prevTaskNode = previousRootTaskNodes.find(
                (taskNode) => taskNode.task?.id === currentTaskNode.task?.id,
            )
            const prevTaskValue = prevTaskNode?.task ?? {}
            const currentTaskValue = currentTaskNode.task ?? {}
            if (
                !prevTaskNode ||
                Object.keys(prevTaskValue).length !== Object.keys(currentTaskValue).length
            ) {
                return false
            }
            if (!isDeepEqual(prevTaskValue, currentTaskValue)) return false
        }
    } while (previousRootTaskNodes.length && currentRootTaskNodes.length && failIndex-- > 0)

    if (failIndex <= 0) {
        console.warn("areTasksIdenticalInGraphUntilTask: Infinite loop detected, stopping comparison.")
        return false
    }

    return true
}
