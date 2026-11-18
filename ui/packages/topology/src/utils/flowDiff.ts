import {isDeepEqual} from "@kestra-io/design-system"
import {
    extractBlockWithPath,
    getPathFromSectionAndId,
    insertBlockWithPath,
    replaceBlockWithPath,
} from "./yaml/blocks"
import {appendKeyToPath, joinPath, parsePath} from "./yaml/paths"
import {parse} from "./yaml/serialization"
import type {FlowGraph, FlowGraphCluster, MinimalNode} from "./vueFlowUtils"
import {CLUSTER_PREFIX} from "./constants"

export type TaskDiffStatus = "added" | "removed" | "changed" | "renamed" | "moved" | "unchanged"

interface PropertyChange {
    key: string;
    before: unknown;
    after: unknown;
}

export interface TaskDiff {
    id: string;
    type: string;
    status: TaskDiffStatus;
    /** `tasks`, `triggers`, `errors`… at the root, `<parentId>.<property>` inside a flowable. */
    section: string;
    previousSection?: string;
    renamedFrom?: string;
    changes: PropertyChange[];
    /** Its own properties, without the tasks it contains: after the change, or before it when removed. */
    properties: Record<string, unknown>;
}

export interface FlowDiff {
    tasks: TaskDiff[];
    counts: Record<TaskDiffStatus, number>;
}

type FlowDocument = Record<string, unknown>

interface TaskEntry {
    id: string;
    type: string;
    section: string;
    previousId?: string;
    properties: Record<string, unknown>;
    childIds: string[];
}

const ROOT_SECTIONS = ["triggers", "tasks", "errors", "finally", "afterExecution"]

/** True for a plain object: not an array, not null. */
function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value)
}

/** True for anything with a string `id` and `type`: a task or a trigger. */
function isTaskLike(value: unknown): value is Record<string, unknown> & {id: string; type: string} {
    return isRecord(value) && typeof value.id === "string" && typeof value.type === "string"
}

/** True when a property holds child tasks, as a list or as named branches of lists. */
function holdsTasks(value: unknown): boolean {
    if (Array.isArray(value)) return value.some(isTaskLike)
    return isRecord(value) && Object.values(value).some((v) => Array.isArray(v) && v.some(isTaskLike))
}

/** The keys of a task that are its own: not `id`, and not the tasks it contains. */
function ownPropertiesOf(item: Record<string, unknown>) {
    return Object.keys(item).filter((key) => key !== "id" && !holdsTasks(item[key]))
}

/** Every task and trigger of a flow by id, with its section, own properties and children. */
function collectTasks(flow: FlowDocument | undefined): Map<string, TaskEntry> {
    const entries = new Map<string, TaskEntry>()

    const visitList = (list: unknown, section: string): string[] => {
        if (!Array.isArray(list)) return []
        const ids: string[] = []
        let previousId: string | undefined
        for (const item of list) {
            if (!isTaskLike(item)) continue
            const {id, type} = item
            const properties = Object.fromEntries(ownPropertiesOf(item).map((key) => [key, item[key]]))
            const entry: TaskEntry = {id, type, section, previousId, properties, childIds: []}
            entries.set(id, entry)
            for (const [key, value] of Object.entries(item)) {
                const branches = Array.isArray(value) ? {[key]: value} : isRecord(value) ? value : {}
                for (const [branch, tasks] of Object.entries(branches)) {
                    const path = Array.isArray(value) ? key : `${key}.${branch}`
                    entry.childIds.push(...visitList(tasks, `${id}.${path}`))
                }
            }
            ids.push(id)
            previousId = id
        }
        return ids
    }

    for (const section of ROOT_SECTIONS) visitList(flow?.[section], section)
    return entries
}

/** The own properties that differ between two versions of a task. */
function propertyChanges(before: TaskEntry, after: TaskEntry): PropertyChange[] {
    const keys = [...new Set([...Object.keys(before.properties), ...Object.keys(after.properties)])]
    return keys
        .filter((key) => !isDeepEqual(before.properties[key], after.properties[key]))
        .map((key) => ({key, before: before.properties[key], after: after.properties[key]}))
}

/** True when two tasks differ by id only, which is how a rename is recognised. */
function sameShape(before: TaskEntry, after: TaskEntry): boolean {
    return isDeepEqual(before.properties, after.properties) && isDeepEqual(before.childIds, after.childIds)
}

/** A diff entry for a task, with no property change listed yet. */
function toDiff({id, type, section, properties}: TaskEntry, status: TaskDiffStatus): TaskDiff {
    return {id, type, status, section, changes: [], properties}
}

/** The flow source as an object, or `undefined` when it is empty or not a map. */
function parseFlow(source: string | undefined): FlowDocument | undefined {
    const parsed = parse<unknown>(source, false)
    return isRecord(parsed) ? parsed : undefined
}

/** Compares two flow sources task by task: added, removed, changed, renamed, moved or unchanged. */
export function diffFlowSources(before: string | undefined, after: string | undefined): FlowDiff {
    const old = collectTasks(parseFlow(before))
    const current = collectTasks(parseFlow(after))

    const renamedTo = new Map<string, string>()
    const renamedFrom = new Map<string, string>()
    const oldOnly = [...old.values()].filter((entry) => !current.has(entry.id))
    for (const entry of current.values()) {
        if (old.has(entry.id)) continue
        const match = oldOnly.find((candidate) => !renamedTo.has(candidate.id) && sameShape(candidate, entry))
        if (match) {
            renamedTo.set(match.id, entry.id)
            renamedFrom.set(entry.id, match.id)
        }
    }

    const mapSection = (section: string) => {
        const [parent, ...rest] = section.split(".")
        const renamed = renamedTo.get(parent)
        return renamed ? [renamed, ...rest].join(".") : section
    }

    const tasks: TaskDiff[] = []
    for (const entry of current.values()) {
        const previous = old.get(entry.id) ?? old.get(renamedFrom.get(entry.id) ?? "")
        if (!previous) {
            tasks.push(toDiff(entry, "added"))
            continue
        }
        const changes = propertyChanges(previous, entry)
        const moved = mapSection(previous.section) !== entry.section
        const status = renamedFrom.has(entry.id)
            ? "renamed"
            : moved ? "moved" : changes.length ? "changed" : "unchanged"
        tasks.push({
            ...toDiff(entry, status),
            previousSection: moved ? previous.section : undefined,
            renamedFrom: renamedFrom.get(entry.id),
            changes,
        })
    }

    for (const entry of old.values()) {
        if (current.has(entry.id) || renamedTo.has(entry.id)) continue
        const removed = toDiff(entry, "removed")
        const anchor = entry.previousId ? renamedTo.get(entry.previousId) ?? entry.previousId : undefined
        const anchorIndex = anchor ? tasks.findIndex((task) => task.id === anchor) : -1
        if (anchorIndex >= 0) tasks.splice(anchorIndex + 1, 0, removed)
        else {
            const sectionIndex = tasks.findIndex((task) => task.section === mapSection(entry.section))
            tasks.splice(sectionIndex >= 0 ? sectionIndex : tasks.length, 0, removed)
        }
    }

    const counts = {added: 0, removed: 0, changed: 0, renamed: 0, moved: 0, unchanged: 0}
    for (const task of tasks) counts[task.status]++
    return {tasks, counts}
}

/** The id of the task or trigger a graph node stands for. */
function nodeTaskId(node: MinimalNode & {trigger?: {id?: string}}): string | undefined {
    return node.task?.id ?? node.trigger?.id
}

/** A lane's start or end dot without the random suffix the server adds on every graph it builds. */
function dotKey(uid: string): string | undefined {
    return /^(.*\.(?:root|end))-[A-Za-z0-9]+$/.exec(uid)?.[1]
}

/** The id of the flowable task a graph cluster stands for. */
function clusterTaskId(cluster: FlowGraphCluster): string | undefined {
    const id = cluster.cluster.taskNode?.task?.id
    return typeof id === "string" ? id : undefined
}

/** One graph for both versions: `after` plus what only removed tasks had, with a status per node. */
export function mergeFlowGraphs(
    before: FlowGraph | undefined,
    after: FlowGraph | undefined,
    diff: FlowDiff,
): {graph: FlowGraph; statusByUid: Map<string, TaskDiffStatus>} {
    const statusById = new Map(diff.tasks.map((task) => [task.id, task.status]))
    const base = after ?? before ?? {nodes: [], edges: []}
    const nodes = [...base.nodes]
    const clusters = (base.clusters ?? []).map((cluster) => ({...cluster, nodes: [...cluster.nodes]}))
    const edges = [...base.edges]

    if (before && after) {
        const afterUids = new Set(after.nodes.map((node) => node.uid))
        const removedUids = before.nodes
            .filter((node) => statusById.get(nodeTaskId(node) ?? "") === "removed")
            .map((node) => node.uid)
        const oldOnly = new Set(
            before.nodes
                .filter((node) => !afterUids.has(node.uid))
                .filter((node) => {
                    const id = nodeTaskId(node)
                    return id
                        ? statusById.get(id) === "removed"
                        : removedUids.some((uid) => node.uid.startsWith(uid + "."))
                })
                .map((node) => node.uid),
        )
        nodes.push(...before.nodes.filter((node) => oldOnly.has(node.uid)))

        const clusterByUid = new Map(clusters.map((cluster) => [cluster.cluster.uid, cluster]))
        for (const cluster of before.clusters ?? []) {
            const existing = clusterByUid.get(cluster.cluster.uid)
            if (existing) {
                existing.nodes.push(
                    ...cluster.nodes.filter((uid) => oldOnly.has(uid) && !existing.nodes.includes(uid)),
                )
            } else if (statusById.get(clusterTaskId(cluster) ?? "") === "removed") {
                clusters.push({
                    ...cluster,
                    nodes: cluster.nodes.filter((uid) => oldOnly.has(uid) || afterUids.has(uid)),
                })
            }
        }

        const afterDots = new Map(after.nodes.flatMap((node) => {
            const key = dotKey(node.uid)
            return key ? [[key, node.uid] as const] : []
        }))
        const alias = (uid: string) => (afterUids.has(uid) ? uid : afterDots.get(dotKey(uid) ?? "") ?? uid)
        const included = new Set([...afterUids, ...oldOnly])
        const neighbours = (uid: string, direction: "source" | "target") => before.edges
            .filter((edge) => edge[direction === "source" ? "target" : "source"] === uid)
            .map((edge) => edge[direction])
        /** A neighbour that moved or was renamed is gone: hop over it to the next one still there. */
        const nearest = (
            uid: string,
            direction: "source" | "target",
            seen = new Set<string>(),
        ): string | undefined => {
            if (included.has(alias(uid))) return alias(uid)
            if (seen.has(uid)) return undefined
            seen.add(uid)
            for (const next of neighbours(uid, direction)) {
                const found = nearest(next, direction, seen)
                if (found) return found
            }
            return undefined
        }
        const seenEdges = new Set(edges.map((edge) => `${edge.source}|${edge.target}`))
        for (const edge of before.edges.filter((e) => oldOnly.has(e.source) || oldOnly.has(e.target))) {
            const source = nearest(edge.source, "source")
            const target = nearest(edge.target, "target")
            if (!source || !target || seenEdges.has(`${source}|${target}`)) continue
            seenEdges.add(`${source}|${target}`)
            edges.push({...edge, source, target})
        }
    }

    const statusByUid = new Map<string, TaskDiffStatus>()
    for (const node of nodes) {
        const status = statusById.get(nodeTaskId(node) ?? "")
        if (status) statusByUid.set(node.uid, status)
    }
    for (const cluster of clusters) {
        const status = statusById.get(clusterTaskId(cluster) ?? "")
        if (status) statusByUid.set(cluster.cluster.uid.replace(CLUSTER_PREFIX, ""), status)
    }

    return {graph: {nodes, clusters, edges}, statusByUid}
}

/** The path of a task in a flow source, such as `tasks[1].then[0]`. */
function pathOf(source: string, id: string): string | undefined {
    for (const section of ROOT_SECTIONS) {
        const path = getPathFromSectionAndId({source, section, id})
        if (path && path !== section) return path
    }
    return undefined
}

/** The `after` flow with the given tasks put back the way they are in `before`. */
export function revertChanges(before: string | undefined, after: string, ids: Iterable<string>): string {
    if (!parseFlow(after)) return after
    const previous = before ?? ""
    const rejected = new Set(ids)
    const diff = diffFlowSources(before, after)
    const renamedTo = new Map(diff.tasks.map((task) => [task.renamedFrom ?? task.id, task.id]))
    const reverted = new Map(
        diff.tasks
            .filter((task) => rejected.has(task.id) && task.status !== "unchanged")
            .map((task) => [task.renamedFrom ?? task.id, task]),
    )
    let source = after

    /** Where a task of `before` is now, under its old id or the one it was renamed to. */
    const locate = (oldId: string) => pathOf(source, oldId) ?? pathOf(source, renamedTo.get(oldId) ?? oldId)

    /** Inserts a task block in the list it had in `before`, after the task it followed there. */
    const putBack = (entry: TaskEntry, newBlock: string | undefined) => {
        if (!newBlock) return
        const [head, ...keys] = entry.section.split(".")
        const parent = keys.length ? locate(head) : head
        const parentPath = parent ? keys.reduce(appendKeyToPath, parent) : "tasks"
        const anchor = parsePath(entry.previousId ? locate(entry.previousId) ?? "" : "")
        const index = joinPath(anchor.slice(0, -1)) === parentPath ? anchor.at(-1) : undefined
        source = insertBlockWithPath({
            source,
            parentPath,
            newBlock,
            refPath: index ?? 0,
            position: index === undefined ? "before" : "after",
        })
    }

    for (const [oldId, task] of reverted) {
        const path = pathOf(source, task.id)
        if (!path) continue
        if (task.status === "added") {
            source = replaceBlockWithPath({source, path, newContent: ""})
            continue
        }
        const oldPath = pathOf(previous, oldId) ?? ""
        const keys = [...task.changes.map((change) => change.key), ...(task.renamedFrom ? ["id"] : [])]
        for (const key of keys) {
            source = replaceBlockWithPath({
                source,
                path: appendKeyToPath(path, key),
                newContent: extractBlockWithPath({
                    source: previous,
                    path: appendKeyToPath(oldPath, key),
                }) ?? "",
            })
        }
    }

    for (const entry of collectTasks(parseFlow(before)).values()) {
        const task = reverted.get(entry.id)
        const path = pathOf(source, entry.id)
        if (task?.status === "removed" && !path) {
            putBack(entry, extractBlockWithPath({source: previous, path: pathOf(previous, entry.id) ?? ""}))
        } else if (task?.previousSection && path) {
            const block = extractBlockWithPath({source, path})
            source = replaceBlockWithPath({source, path, newContent: ""})
            putBack(entry, block)
        }
    }
    return source
}
