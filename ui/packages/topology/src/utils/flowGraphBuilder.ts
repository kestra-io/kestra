import type {BranchType, Cluster, FlowGraph, FlowGraphCluster, MinimalNode} from "./vueFlowUtils"

/**
 * Port of the backend `GraphUtils` for a flow without execution: change both together.
 * `flowGraphBuilder.test.ts` compares it with fixtures generated from the Java builder.
 */

type RelationType = "SEQUENTIAL" | "CHOICE" | "ERROR" | "FINALLY" | "AFTER_EXECUTION" | "PARALLEL" | "DYNAMIC"

interface Relation {
    relationType?: RelationType;
    value?: string;
}

type Task = {id: string; type: string} & Record<string, unknown>

interface DagTask {
    task: Task;
    dependsOn?: string[];
}

const HIERARCHIES = "io.kestra.core.models.hierarchies"
const CORE_FLOW = "io.kestra.plugin.core.flow"
const SUBFLOW_TYPE = `${CORE_FLOW}.Subflow`

// The backend draws a random id here on every build; a fixed one keeps a lane's dots stable across edits.
// It stays alphanumeric: `dotKey` in flowDiff.ts recognises a dot by that shape.
const POINT_ID = "local"

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value)
}

function toTask(value: unknown): Task | undefined {
    if (!isRecord(value) || typeof value.type !== "string") return undefined
    if (typeof value.id === "string") return value as Task
    return typeof value.id === "number" ? {...value, id: String(value.id), type: value.type} : undefined
}

/** A task still being typed has no id or type yet: it is left out until it has both. */
function tasksOf(value: unknown): Task[] {
    if (!Array.isArray(value)) return []
    return value.flatMap((item) => {
        const task = toTask(item)
        return task ? [task] : []
    })
}

function dagTasksOf(value: unknown): DagTask[] {
    if (!Array.isArray(value)) return []
    return value.flatMap((item) => {
        const task = isRecord(item) ? toTask(item.task) : undefined
        if (!task || !isRecord(item)) return []
        return [{task, dependsOn: Array.isArray(item.dependsOn) ? item.dependsOn.map(String) : undefined}]
    })
}

class GraphNode {
    branchType?: BranchType

    constructor(public uid: string, readonly type: string) {}

    getUid(): string {
        return this.uid
    }

    updateUidWithChildren(uid: string) {
        this.uid = uid
    }

    updateWithChildren(branchType: BranchType) {
        this.branchType = branchType
    }

    toWire(): MinimalNode {
        return {uid: this.getUid(), type: this.type, ...(this.branchType && {branchType: this.branchType})}
    }
}

class TaskNode extends GraphNode {
    constructor(readonly task: Task, readonly relationType: RelationType, private readonly subflow = false) {
        super(task.id, `${HIERARCHIES}.${subflow ? "SubflowGraphTask" : "GraphTask"}`)
    }

    override toWire(): MinimalNode {
        return {
            ...super.toWire(),
            task: this.task,
            relationType: this.relationType,
            ...(this.subflow && {disabled: false}),
        }
    }
}

class TriggerNode extends GraphNode {
    constructor(readonly triggerDeclaration: Task) {
        super(triggerDeclaration.id, `${HIERARCHIES}.GraphTrigger`)
    }

    override toWire(): MinimalNode {
        return {...super.toWire(), triggerDeclaration: this.triggerDeclaration}
    }
}

/** Insertion-ordered like the Guava graph behind the backend's `Graph`; an edge adds the nodes it does not know. */
class DirectedGraph {
    private readonly successors = new Map<GraphNode, Map<GraphNode, Relation>>()

    addNode(node: GraphNode) {
        if (!this.successors.has(node)) this.successors.set(node, new Map())
    }

    addEdge(source: GraphNode, target: GraphNode, relation: Relation) {
        this.addNode(source)
        this.addNode(target)
        this.successors.get(source)?.set(target, relation)
    }

    removeEdge(source: GraphNode, target: GraphNode) {
        this.successors.get(source)?.delete(target)
    }

    removeNode(node: GraphNode) {
        this.successors.delete(node)
        for (const targets of this.successors.values()) targets.delete(node)
    }

    nodes(): GraphNode[] {
        return [...this.successors.keys()]
    }

    edges(): {source: GraphNode; target: GraphNode; relation: Relation}[] {
        return [...this.successors].flatMap(([source, targets]) =>
            [...targets].map(([target, relation]) => ({source, target, relation})))
    }
}

class ClusterNode extends GraphNode {
    readonly graph = new DirectedGraph()
    readonly root = new GraphNode(`root-${POINT_ID}`, `${HIERARCHIES}.GraphClusterRoot`)
    readonly finally = new GraphNode(`finally-${POINT_ID}`, `${HIERARCHIES}.GraphClusterFinally`)
    readonly afterExecution = new GraphNode(`after-execution-${POINT_ID}`, `${HIERARCHIES}.GraphClusterAfterExecution`)
    readonly end = new GraphNode(`end-${POINT_ID}`, `${HIERARCHIES}.GraphClusterEnd`)

    constructor(uid: string, readonly relationType?: RelationType, readonly taskNode?: TaskNode) {
        super(uid, `${HIERARCHIES}.GraphCluster`)
        for (const node of [this.root, this.finally, this.afterExecution, this.end]) this.addNode(node)
        this.addEdge(this.finally, this.afterExecution)
        this.addEdge(this.afterExecution, this.end)
    }

    static forTask(task: Task, relationType: RelationType): ClusterNode {
        const taskNode = new TaskNode(task, relationType)
        const cluster = new ClusterNode(task.id, relationType, taskNode)
        cluster.graph.addNode(taskNode)
        cluster.addEdge(cluster.root, taskNode)
        return cluster
    }

    addNode(node: GraphNode) {
        node.updateUidWithChildren(`${this.uid}.${node.uid}`)
        this.graph.addNode(node)
    }

    addEdge(source: GraphNode, target: GraphNode, relation: Relation = {}) {
        this.graph.addEdge(source, target, relation)
    }

    /** Where an edge leaving `node` starts: a cluster is left through its end. */
    static exit(node: GraphNode): GraphNode {
        return node instanceof ClusterNode ? node.end : node
    }

    /** Where an edge reaching `node` lands: a cluster is entered through its root. */
    static entry(node: GraphNode): GraphNode {
        return node instanceof ClusterNode ? node.root : node
    }

    override getUid(): string {
        return `cluster_${this.uid.replaceAll("cluster_", "")}`
    }

    override updateUidWithChildren(uid: string) {
        for (const node of this.graph.nodes()) {
            // the root and end of a nested cluster sit here only as edge endpoints, their own cluster renames them
            const foreignEndpoint = (node.type.endsWith("GraphClusterRoot") || node.type.endsWith("GraphClusterEnd"))
                && node !== this.root && node !== this.end
            if (!foreignEndpoint) node.updateUidWithChildren(uid + node.uid.substring(this.uid.length))
        }
        this.uid = uid
    }

    override updateWithChildren(branchType: BranchType) {
        this.branchType = branchType
        this.root.branchType = branchType
        this.end.branchType = branchType
        if (this.taskNode) this.taskNode.branchType = branchType
    }

    toWireCluster(): Cluster {
        return {
            uid: this.getUid(),
            type: this.type,
            ...(this.branchType && {branchType: this.branchType}),
            ...(this.relationType && {relationType: this.relationType}),
            taskNode: this.taskNode ? this.taskNode.toWire() as Cluster["taskNode"] : null,
        }
    }
}

function removeFinally(graph: ClusterNode) {
    for (const edge of graph.graph.edges()) {
        if (edge.target === graph.finally) graph.addEdge(edge.source, graph.afterExecution, edge.relation)
    }
    graph.graph.removeNode(graph.finally)
}

function removeAfterExecution(graph: ClusterNode) {
    for (const edge of graph.graph.edges()) {
        if (edge.target === graph.afterExecution) graph.addEdge(edge.source, graph.end, edge.relation)
    }
    graph.graph.removeNode(graph.afterExecution)
}

function fillGraph(
    graph: ClusterNode,
    tasks: Task[],
    relationType: RelationType,
    value?: string,
    start?: GraphNode,
    firstRelationType?: RelationType,
): GraphNode {
    let previous: GraphNode = start ?? graph.taskNode ?? graph.root
    if (relationType === "FINALLY") {
        previous = graph.finally
        graph.graph.removeEdge(graph.finally, graph.afterExecution)
    }
    if (relationType === "AFTER_EXECUTION") {
        previous = graph.afterExecution
        graph.graph.removeEdge(graph.afterExecution, graph.end)
    }
    const nextEdge = relationType === "AFTER_EXECUTION"
        ? graph.end
        : relationType === "FINALLY" ? graph.afterExecution : graph.finally
    const allLinkToEnd = relationType === "PARALLEL" || relationType === "CHOICE"
    const isAlternative = relationType === "ERROR" || relationType === "FINALLY" || relationType === "AFTER_EXECUTION"

    tasks.forEach((task, index) => {
        const isFirst = index === 0
        let newRelation = firstRelationType ?? graph.relationType
        if (relationType === "ERROR") {
            newRelation = relationType
        } else if (!isFirst && relationType !== "PARALLEL" && graph.relationType !== "DYNAMIC") {
            newRelation = relationType
        }

        const current = tasksTree(task)
            ?? new TaskNode(task, relationType, task.type === SUBFLOW_TYPE)
        graph.addNode(current)
        if (isAlternative) current.updateWithChildren(relationType as BranchType)
        if (relationType === "ERROR" && isFirst) previous = graph.root

        graph.addEdge(ClusterNode.exit(previous), ClusterNode.entry(current), {
            ...(newRelation && {relationType: newRelation}),
            ...(value !== undefined && {value}),
        })
        if (relationType !== "PARALLEL") previous = current
        if (allLinkToEnd || index === tasks.length - 1) graph.addEdge(ClusterNode.exit(current), nextEdge)
    })

    return previous
}

function fillAlternativePaths(graph: ClusterNode, errors: unknown, finallyTasks: unknown, afterExecution?: unknown) {
    if (tasksOf(errors).length) fillGraph(graph, tasksOf(errors), "ERROR")

    if (tasksOf(finallyTasks).length) fillGraph(graph, tasksOf(finallyTasks), "FINALLY")
    else removeFinally(graph)

    if (tasksOf(afterExecution).length) fillGraph(graph, tasksOf(afterExecution), "AFTER_EXECUTION")
    else removeAfterExecution(graph)
}

function fillGraphDag(graph: ClusterNode, dagId: string, tasks: DagTask[]) {
    const createdTasks: TaskNode[] = []
    const createdIds: string[] = []
    const dependencies = new Set(tasks.flatMap((task) => task.dependsOn ?? []))
    const previous = graph.taskNode ?? graph.root
    const relation: Relation = {relationType: "PARALLEL"}

    while (createdIds.length < tasks.length) {
        const createdBefore = createdIds.length
        for (const {task, dependsOn} of tasks) {
            if (createdIds.includes(task.id) || dependsOn?.some((id) => !createdIds.includes(id))) continue

            // as on the backend, a subflow inside a Dag is drawn as a plain task
            const current = tasksTree(task) ?? new TaskNode(task, "SEQUENTIAL")
            graph.addNode(current)

            if (!dependsOn) graph.addEdge(previous, ClusterNode.entry(current), relation)
            for (const dependency of dependsOn ?? []) {
                const dependencyTask = createdTasks.find((node) => node.task.id === dependency)
                if (dependencyTask && !isSupportedFlowableType(dependencyTask.task.type)) {
                    graph.addEdge(dependencyTask, ClusterNode.entry(current), relation)
                    continue
                }
                const dependencyCluster = graph.graph.nodes()
                    .find((node) => node instanceof ClusterNode && node.getUid().endsWith(dependency))
                if (dependencyCluster) graph.addEdge(ClusterNode.exit(dependencyCluster), ClusterNode.entry(current), relation)
            }
            if (!dependencies.has(task.id)) graph.addEdge(ClusterNode.exit(current), graph.finally)

            if (current instanceof TaskNode) createdTasks.push(current)
            createdIds.push(task.id)
        }
        if (createdIds.length === createdBefore) {
            throw new Error(`Cannot order the tasks of Dag '${dagId}': a dependency is missing, cyclic or declared twice.`)
        }
    }
}

function branch(clusterRelation: RelationType, tasksRelation: RelationType) {
    return (task: Task): GraphNode => {
        const cluster = ClusterNode.forTask(task, clusterRelation)
        fillGraph(cluster, tasksOf(task.tasks), tasksRelation)
        fillAlternativePaths(cluster, task.errors, task.finally)
        return cluster
    }
}

function switchTree(task: Task): GraphNode {
    const cluster = ClusterNode.forTask(task, "CHOICE")
    fillGraph(cluster, tasksOf(task.defaults), "SEQUENTIAL", "defaults")
    for (const [value, tasks] of Object.entries(isRecord(task.cases) ? task.cases : {})) {
        fillGraph(cluster, tasksOf(tasks), "SEQUENTIAL", value)
    }
    fillAlternativePaths(cluster, task.errors, task.finally)
    return cluster
}

function ifTree(task: Task): GraphNode {
    const cluster = ClusterNode.forTask(task, "CHOICE")
    fillGraph(cluster, tasksOf(task.then), "SEQUENTIAL", "then")
    fillGraph(cluster, tasksOf(task.else), "SEQUENTIAL", "else")
    fillAlternativePaths(cluster, task.errors, task.finally)
    return cluster
}

function dagTree(task: Task): GraphNode {
    const cluster = ClusterNode.forTask(task, "DYNAMIC")
    fillGraphDag(cluster, task.id, dagTasksOf(task.tasks))
    fillAlternativePaths(cluster, task.errors, task.finally)
    return cluster
}

function pauseTree(task: Task): GraphNode {
    if (!tasksOf(task.errors).length && !tasksOf(task.finally).length) return new TaskNode(task, "SEQUENTIAL")

    const cluster = ClusterNode.forTask(task, "SEQUENTIAL")
    fillGraph(cluster, tasksOf([task.onPause]), "SEQUENTIAL")
    fillAlternativePaths(cluster, task.errors, task.finally)
    return cluster
}

function approvalTree(task: Task): GraphNode {
    const cluster = ClusterNode.forTask(task, "CHOICE")
    const onWait = tasksOf(task.onWait)
    const cases = {approve: tasksOf(task.onApprove), deny: tasksOf(task.onDeny)}
    const start = cluster.taskNode ?? cluster.root
    if (!onWait.length && !cases.approve.length && !cases.deny.length) cluster.addEdge(start, cluster.finally)

    let last: GraphNode | undefined
    if (onWait.length) {
        last = fillGraph(cluster, onWait, "SEQUENTIAL", undefined, undefined, "SEQUENTIAL")
        // an empty case still leaves through finally, so the edge to it is only dropped when every case has tasks
        if (cases.approve.length && cases.deny.length) cluster.graph.removeEdge(ClusterNode.exit(last), cluster.finally)
    }
    for (const [value, tasks] of Object.entries(cases)) fillGraph(cluster, tasks, "SEQUENTIAL", value, last)
    fillAlternativePaths(cluster, task.errors, task.finally)
    return cluster
}

const sequential = branch("SEQUENTIAL", "SEQUENTIAL")

/** One entry per flowable of the core plugin, mirroring its `tasksTree`; `WaitFor` is the alias of `LoopUntil`. */
const FLOWABLE_TREES = new Map<string, (task: Task) => GraphNode>([
    [`${CORE_FLOW}.Sequential`, sequential],
    [`${CORE_FLOW}.WorkingDirectory`, sequential],
    [`${CORE_FLOW}.AllowFailure`, sequential],
    [`${CORE_FLOW}.LoopUntil`, sequential],
    [`${CORE_FLOW}.WaitFor`, sequential],
    [`${CORE_FLOW}.Loop`, branch("DYNAMIC", "SEQUENTIAL")],
    [`${CORE_FLOW}.Parallel`, branch("PARALLEL", "PARALLEL")],
    [`${CORE_FLOW}.Switch`, switchTree],
    [`${CORE_FLOW}.If`, ifTree],
    [`${CORE_FLOW}.Dag`, dagTree],
    [`${CORE_FLOW}.Pause`, pauseTree],
    [`${CORE_FLOW}.Approval`, approvalTree],
])

function tasksTree(task: Task): GraphNode | undefined {
    return FLOWABLE_TREES.get(task.type)?.(task)
}

/** Whether `buildFlowGraph` knows the subgraph of this flowable type. */
export function isSupportedFlowableType(type: string): boolean {
    return FLOWABLE_TREES.has(type)
}

/**
 * The flowable types used in `flow` that `buildFlowGraph` would draw as a plain task, because their subgraph is
 * defined by a plugin. `isFlowable` is the caller's knowledge of the installed plugins.
 */
export function unsupportedFlowableTypes(flow: unknown, isFlowable: (type: string) => boolean): string[] {
    const found = new Set<string>()
    const visit = (value: unknown) => {
        if (isRecord(value) && typeof value.type === "string" && !isSupportedFlowableType(value.type) && isFlowable(value.type)) {
            found.add(value.type)
        }
        if (typeof value === "object" && value !== null) Object.values(value).forEach(visit)
    }
    visit(flow)
    return [...found]
}

function flattenNodes(cluster: ClusterNode): GraphNode[] {
    return cluster.graph.nodes().flatMap((node) => (node instanceof ClusterNode ? flattenNodes(node) : [node]))
}

function flattenEdges(cluster: ClusterNode): FlowGraph["edges"] {
    const nested = cluster.graph.nodes().flatMap((node) => (node instanceof ClusterNode ? flattenEdges(node) : []))
    return [
        ...cluster.graph.edges().map(({source, target, relation}) => ({
            source: source.getUid(),
            target: target.getUid(),
            relation: {...relation},
        })),
        ...nested,
    ]
}

function flattenClusters(cluster: ClusterNode, parents: string[]): FlowGraphCluster[] {
    return cluster.graph.nodes().flatMap((node) => {
        if (!(node instanceof ClusterNode)) return []
        return [
            {
                cluster: node.toWireCluster(),
                nodes: node.graph.nodes().map((child) => child.getUid()),
                parents,
                start: node.root.getUid(),
                end: node.end.getUid(),
            },
            ...flattenClusters(node, [...parents, node.getUid()]),
        ]
    })
}

/**
 * The topology graph of a parsed flow, as `POST /flows/graph` answers with no subflow expanded; throws when a Dag
 * cannot be ordered. A flowable it has no shape for is drawn as a plain task: see `unsupportedFlowableTypes`.
 */
export function buildFlowGraph(flow: unknown): FlowGraph {
    const source = isRecord(flow) ? flow : {}
    const graph = new ClusterNode("root")

    if (Array.isArray(source.triggers)) {
        const triggers = new ClusterNode("Triggers")
        graph.addNode(triggers)
        for (const trigger of tasksOf(source.triggers)) {
            const node = new TriggerNode(trigger)
            triggers.addNode(node)
            triggers.addEdge(triggers.root, node)
            triggers.addEdge(node, triggers.end)
        }
        removeFinally(triggers)
        removeAfterExecution(triggers)
        graph.addEdge(triggers.end, graph.root)
    }

    fillGraph(graph, tasksOf(source.tasks), "SEQUENTIAL")
    fillAlternativePaths(graph, source.errors, source.finally, source.afterExecution)

    return {
        nodes: [...new Set(flattenNodes(graph))].map((node) => node.toWire()),
        edges: flattenEdges(graph),
        clusters: flattenClusters(graph, []),
    }
}
