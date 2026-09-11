import * as flowYamlUtils from "@kestra-io/topology/flow-yaml-utils"
import {displayTaskOf} from "../../../utils/flowableBlockOps"
import {ALL_SECTIONS, NESTED_BLOCK_KEYS} from "./blockSections"

export interface TopologyFocusNode {
    id: string
    path: string
    parentPath: string
    depth: number
    /** The lane holding this node's own children, when it is a flowable that has any. */
    childrenPath?: string
}

function laneOf(item: Record<string, unknown>, itemPath: string): {key: string; path: string} | undefined {
    const task = displayTaskOf(item)
    for (const key of NESTED_BLOCK_KEYS) {
        const branch = task[key]
        if (Array.isArray(branch) && branch.length > 0) {
            const taskPath = task === item ? itemPath : `${itemPath}.task`
            return {key, path: `${taskPath}.${key}`}
        }
    }
    const cases = task.cases
    if (cases && typeof cases === "object" && !Array.isArray(cases)) {
        const firstCase = Object.keys(cases as Record<string, unknown>)[0]
        if (firstCase !== undefined) {
            const taskPath = task === item ? itemPath : `${itemPath}.task`
            return {key: "cases", path: `${taskPath}.cases.${firstCase}`}
        }
    }
    return undefined
}

function walk(
    items: unknown,
    parentPath: string,
    depth: number,
    out: TopologyFocusNode[],
): void {
    if (!Array.isArray(items)) return
    items.forEach((raw, index) => {
        if (!raw || typeof raw !== "object") return
        const item = raw as Record<string, unknown>
        const task = displayTaskOf(item)
        const id = task?.id
        if (id == null) return
        const path = `${parentPath}[${index}]`
        const lane = laneOf(item, path)
        out.push({id: String(id), path, parentPath, depth, childrenPath: lane?.path})
        if (lane) walk(getIn(item, lane.path, path), lane.path, depth + 1, out)
    })
}

function getIn(item: Record<string, unknown>, lanePath: string, itemPath: string): unknown {
    const relative = lanePath.slice(itemPath.length).replace(/^\./, "")
    let cur: unknown = item
    for (const segment of relative.split(".")) {
        if (!cur || typeof cur !== "object") return undefined
        cur = (cur as Record<string, unknown>)[segment]
    }
    return cur
}

/**
 * Flattens the flow into the order the arrow keys walk, depth-first so a flowable is immediately
 * followed by its own children. Mirrors the No-code canvas, so the shared keymap means the same
 * thing on both surfaces.
 */
export function buildTopologyFocusOrder(source: string): TopologyFocusNode[] {
    const parsed = flowYamlUtils.parse<Record<string, unknown>>(source)
    if (!parsed) return []
    const out: TopologyFocusNode[] = []
    for (const section of ALL_SECTIONS) {
        walk(parsed[section], section, 0, out)
    }
    return out
}

export function siblingsOf(order: TopologyFocusNode[], id: string): TopologyFocusNode[] {
    const node = order.find(entry => entry.id === id)
    if (!node) return []
    return order.filter(entry => entry.parentPath === node.parentPath)
}

export function moveWithinSiblings(
    order: TopologyFocusNode[],
    id: string | undefined,
    delta: number,
): string | undefined {
    if (!id) return order[0]?.id
    const siblings = siblingsOf(order, id)
    const index = siblings.findIndex(entry => entry.id === id)
    if (index === -1) return order[0]?.id
    const next = siblings[index + delta]
    return next?.id ?? id
}

export function firstChildOf(order: TopologyFocusNode[], id: string | undefined): string | undefined {
    if (!id) return undefined
    const node = order.find(entry => entry.id === id)
    if (!node?.childrenPath) return undefined
    return order.find(entry => entry.parentPath === node.childrenPath)?.id
}

export function parentOf(order: TopologyFocusNode[], id: string | undefined): string | undefined {
    if (!id) return undefined
    const node = order.find(entry => entry.id === id)
    if (!node || node.depth === 0) return undefined
    return order.find(entry => entry.childrenPath === node.parentPath)?.id
}
