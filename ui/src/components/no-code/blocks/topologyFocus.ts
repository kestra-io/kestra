import * as flowYamlUtils from "@kestra-io/topology/flow-yaml-utils"
import {displayTaskOf, taskEditPathFor} from "../../../utils/flowableBlockOps"
import {NESTED_BLOCK_KEYS, TASK_SECTIONS} from "./blockSections"

export interface TopologyFocusNode {
    id: string
    path: string
    parentPath: string
    depth: number
    /** Every lane holding this node's own children — both `If` branches, each `Switch` case, a flowable's `errors`. */
    childrenPaths: string[]
}

function lanesOf(item: Record<string, unknown>, itemPath: string): {key: string; path: string; items: unknown[]}[] {
    const task = displayTaskOf(item)
    const taskPath = taskEditPathFor(itemPath, item)
    const lanes: {key: string; path: string; items: unknown[]}[] = []
    for (const key of NESTED_BLOCK_KEYS) {
        const branch = task[key]
        if (Array.isArray(branch) && branch.length > 0) lanes.push({key, path: `${taskPath}.${key}`, items: branch})
    }
    const cases = task.cases
    if (cases && typeof cases === "object" && !Array.isArray(cases)) {
        for (const caseKey of Object.keys(cases as Record<string, unknown>)) {
            const branch = (cases as Record<string, unknown>)[caseKey]
            if (Array.isArray(branch) && branch.length > 0) {
                lanes.push({
                    key: "cases",
                    path: flowYamlUtils.appendKeyToPath(`${taskPath}.cases`, caseKey),
                    items: branch,
                })
            }
        }
    }
    return lanes
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
        const lanes = lanesOf(item, path)
        out.push({id: String(id), path, parentPath, depth, childrenPaths: lanes.map(lane => lane.path)})
        for (const lane of lanes) {
            walk(lane.items, lane.path, depth + 1, out)
        }
    })
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
    // `triggers` is left out: the shortcuts act through the task helpers, so focusing a trigger
    // would only add a stop where nothing can happen.
    for (const section of TASK_SECTIONS) {
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
    if (!node?.childrenPaths.length) return undefined
    return order.find(entry => node.childrenPaths.includes(entry.parentPath))?.id
}

export function parentOf(order: TopologyFocusNode[], id: string | undefined): string | undefined {
    if (!id) return undefined
    const node = order.find(entry => entry.id === id)
    if (!node || node.depth === 0) return undefined
    return order.find(entry => entry.childrenPaths.includes(node.parentPath))?.id
}
