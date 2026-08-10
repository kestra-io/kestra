import {flowYamlUtils} from "@kestra-io/topology"

export type BlockSection = "tasks" | "triggers" | "errors" | "finally" | "afterExecution"

const FLOWABLE_BRANCH_KEYS = ["tasks", "then", "else", "errors", "finally", "defaults", "cases"] as const

// Ids are unique flow-wide, so collecting them must also reach the sections that only exist at the root.
const ID_BEARING_KEYS = [...FLOWABLE_BRANCH_KEYS, "triggers", "afterExecution"] as const

export const FLOWABLE_SUFFIXES = [
    "If", "Switch", "Parallel", "Sequential", "ForEach",
    "EachSequential", "Dag", "WaitFor", "ForEachItem",
] as const

export function isFlowableType(
    type: string,
    icons?: Record<string, {flowable: boolean}>,
): boolean {
    if (FLOWABLE_SUFFIXES.some(suffix => type.endsWith(`.${suffix}`))) {
        return true
    }
    return icons?.[type]?.flowable ?? false
}

export function isWrappedLaneItem(item: unknown): item is {task: Record<string, unknown>; dependsOn?: unknown} {
    if (!item || typeof item !== "object" || Array.isArray(item)) return false
    const obj = item as Record<string, unknown>
    return obj.type === undefined && Boolean(obj.task) && typeof obj.task === "object" && !Array.isArray(obj.task)
}

export function displayTaskOf(item: Record<string, unknown>): Record<string, unknown> {
    return isWrappedLaneItem(item) ? item.task : item
}

export function taskEditPathFor(itemPath: string, item: Record<string, unknown>): string {
    return isWrappedLaneItem(item) ? `${itemPath}.task` : itemPath
}

export function updateBlock(source: string, section: BlockSection, id: string, newContent: string): string {
    const existing = flowYamlUtils.extractBlock({source, section, key: id})
    if (!existing) return source
    const path = flowYamlUtils.getPathFromSectionAndId({source, section, id})
    if (!path) return source
    return flowYamlUtils.replaceBlockWithPath({source, path, newContent})
}

export function updateBlockAtPath(source: string, path: string, newContent: string): string {
    return flowYamlUtils.replaceBlockWithPath({source, path, newContent})
}

export interface BlockRef {
    section: BlockSection
    id: string
}

const domIdCache = new WeakMap<object, string[]>()

function computeBlockDomIds(items: Record<string, unknown>[]): string[] {
    const firstIndexById = new Map<string, number>()
    return items.map((item, index) => {
        if (!item) return String(index)
        const displayItem = displayTaskOf(item)
        if (displayItem.id == null) return String(index)
        const id = String(displayItem.id)
        if (!firstIndexById.has(id)) firstIndexById.set(id, index)
        return firstIndexById.get(id) === index ? id : `${id}#${index}`
    })
}

export function resolveBlockDomId(items: Record<string, unknown>[], index: number): string {
    let ids = domIdCache.get(items)
    if (!ids) {
        ids = computeBlockDomIds(items)
        domIdCache.set(items, ids)
    }
    return ids[index] ?? String(index)
}

export function addBlock(source: string, section: BlockSection, block: Record<string, unknown>, afterId?: string): string {
    const refPath = afterId !== undefined
        ? (() => {
            const path = flowYamlUtils.getPathFromSectionAndId({source, section, id: afterId})
            if (!path) return undefined
            const match = path.match(/\[(\d+)\]$/)
            return match ? parseInt(match[1], 10) : undefined
        })()
        : undefined

    return flowYamlUtils.insertBlockWithPath({
        source,
        parentPath: section,
        newBlock: flowYamlUtils.stringify(block),
        refPath,
        position: "after",
    })
}

export function addBlockAtPath(
    source: string,
    parentPath: string,
    block: Record<string, unknown>,
    refIndex?: number,
    position: "before" | "after" = "after",
): string {
    return flowYamlUtils.insertBlockWithPath({
        source,
        parentPath,
        newBlock: flowYamlUtils.stringify(block),
        refPath: refIndex,
        position,
    })
}

export function deleteBlock(source: string, section: BlockSection, id: string): string {
    return flowYamlUtils.deleteBlock({source, section, key: id})
}

export function deleteBlockAtPath(source: string, path: string): string {
    const afterDelete = flowYamlUtils.replaceBlockWithPath({source, path, newContent: ""})
    return flowYamlUtils.pruneEmptySequences(afterDelete)
}

export function duplicateBlock(source: string, section: BlockSection, id: string): string {
    const blockYaml = flowYamlUtils.extractBlock({source, section, key: id})
    if (!blockYaml) return source

    const parsed = flowYamlUtils.parse<Record<string, unknown>>(blockYaml)
    if (!parsed) return source

    const existingIds = collectAllIds(source)
    const newId = uniqueId(String(parsed.id), existingIds)
    existingIds.add(newId)
    const duplicate = renameNestedIds({...parsed, id: newId}, existingIds)

    const path = flowYamlUtils.getPathFromSectionAndId({source, section, id})
    const match = path?.match(/\[(\d+)\]$/)
    const refPath = match ? parseInt(match[1], 10) : undefined

    return flowYamlUtils.insertBlockWithPath({
        source,
        parentPath: section,
        newBlock: flowYamlUtils.stringify(duplicate),
        refPath,
        position: "after",
    })
}

export function duplicateBlockAtPath(source: string, path: string): string {
    const blockYaml = flowYamlUtils.extractBlockWithPath({source, path})
    if (!blockYaml) return source

    const parsed = flowYamlUtils.parse<Record<string, unknown>>(blockYaml)
    if (!parsed) return source

    const existingIds = collectAllIds(source)
    const displayItem = displayTaskOf(parsed)
    const newId = uniqueId(String(displayItem.id), existingIds)
    existingIds.add(newId)
    const duplicate = isWrappedLaneItem(parsed)
        ? {...parsed, task: renameNestedIds({...displayItem, id: newId}, existingIds)}
        : renameNestedIds({...parsed, id: newId}, existingIds)

    const parentPath = pathParent(path)
    const match = path.match(/\[(\d+)\]$/)
    const refPath = match ? parseInt(match[1], 10) : undefined

    return flowYamlUtils.insertBlockWithPath({
        source,
        parentPath,
        newBlock: flowYamlUtils.stringify(duplicate),
        refPath,
        position: "after",
    })
}

function pathParent(path: string): string {
    const lastBracket = path.lastIndexOf("[")
    if (lastBracket !== -1) return path.slice(0, lastBracket)
    const lastDot = path.lastIndexOf(".")
    if (lastDot !== -1) return path.slice(0, lastDot)
    return path
}

function uniqueId(baseId: string, existingIds: Set<string>): string {
    const candidate = `${baseId}_copy`
    if (!existingIds.has(candidate)) return candidate
    let counter = 2
    while (existingIds.has(`${candidate}_${counter}`)) counter++
    return `${candidate}_${counter}`
}

function renameNestedIds(
    node: Record<string, unknown>,
    takenIds: Set<string>,
): Record<string, unknown> {
    const result: Record<string, unknown> = {...node}
    for (const key of FLOWABLE_BRANCH_KEYS) {
        const val = node[key]
        if (Array.isArray(val)) {
            result[key] = (val as Record<string, unknown>[]).map(item =>
                renameTaskNode(item, takenIds),
            )
        } else if (key === "cases" && val && typeof val === "object" && !Array.isArray(val)) {
            const casesObj = val as Record<string, unknown>
            const newCases: Record<string, unknown> = {}
            for (const [caseKey, caseVal] of Object.entries(casesObj)) {
                if (Array.isArray(caseVal)) {
                    newCases[caseKey] = (caseVal as Record<string, unknown>[]).map(item =>
                        renameTaskNode(item, takenIds),
                    )
                } else {
                    newCases[caseKey] = caseVal
                }
            }
            result[key] = newCases
        }
    }
    return result
}

function renameTaskNode(
    node: Record<string, unknown>,
    takenIds: Set<string>,
): Record<string, unknown> {
    if (!node || typeof node !== "object") return node
    if (isWrappedLaneItem(node)) return {...node, task: renameTaskNode(node.task, takenIds)}
    const originalId = typeof node.id === "string" ? node.id : undefined
    if (originalId === undefined) return renameNestedIds(node, takenIds)
    const newId = uniqueId(originalId, takenIds)
    takenIds.add(newId)
    return renameNestedIds({...node, id: newId}, takenIds)
}

function collectAllIds(source: string): Set<string> {
    const ids = new Set<string>()
    try {
        const parsed = flowYamlUtils.parse<Record<string, unknown>>(source)
        walkIds(parsed, ids)
    } catch {
    }
    return ids
}

function walkIds(node: unknown, ids: Set<string>): void {
    if (!node || typeof node !== "object") return
    if (Array.isArray(node)) {
        for (const item of node) walkIds(item, ids)
        return
    }
    if (isWrappedLaneItem(node)) {
        walkIds(node.task, ids)
        return
    }
    const obj = node as Record<string, unknown>
    if ("id" in obj && typeof obj.id === "string") ids.add(obj.id)
    for (const key of ID_BEARING_KEYS) {
        const val = obj[key]
        if (Array.isArray(val)) {
            for (const item of val) walkIds(item, ids)
        } else if (val && typeof val === "object") {
            for (const caseVal of Object.values(val as Record<string, unknown>)) {
                if (Array.isArray(caseVal)) {
                    for (const item of caseVal) walkIds(item, ids)
                }
            }
        }
    }
}

export function reorderAtPath(source: string, parentPath: string, fromIndex: number, toIndex: number): string {
    if (fromIndex === toIndex) return source
    try {
        const parsed = flowYamlUtils.parse<Record<string, unknown>>(source)
        if (!parsed) return source

        const list = getAtPath(parsed, parentPath)
        if (!Array.isArray(list)) return source
        if (fromIndex < 0 || fromIndex >= list.length || toIndex < 0 || toIndex >= list.length) return source

        const copy = [...list]
        const [item] = copy.splice(fromIndex, 1)
        copy.splice(toIndex, 0, item)

        setAtPath(parsed, parentPath, copy)
        return flowYamlUtils.stringify(parsed)
    } catch {
        return source
    }
}

export function moveBlockAtPath(source: string, path: string, direction: "up" | "down"): string {
    const match = path.match(/^(.*)\[(\d+)\]$/)
    if (!match) return source

    const parentPath = match[1]
    const index = parseInt(match[2], 10)

    try {
        const parsed = flowYamlUtils.parse<Record<string, unknown>>(source)
        if (!parsed) return source

        const list = getAtPath(parsed, parentPath)
        if (!Array.isArray(list)) return source

        const targetIndex = direction === "up" ? index - 1 : index + 1
        if (targetIndex < 0 || targetIndex >= list.length) return source

        const copy = [...list]
        const tmp = copy[index]
        copy[index] = copy[targetIndex]
        copy[targetIndex] = tmp

        setAtPath(parsed, parentPath, copy)
        return flowYamlUtils.stringify(parsed)
    } catch {
        return source
    }
}

const UNSAFE_KEYS = new Set(["__proto__", "constructor", "prototype"])

function getAtPath(obj: Record<string, unknown>, path: string): unknown {
    const segments = flowYamlUtils.parsePath(path)
    let cur: unknown = obj
    for (const seg of segments) {
        if (cur == null || typeof cur !== "object") return undefined
        if (Array.isArray(cur)) {
            cur = (cur as unknown[])[Number(seg)]
        } else {
            const key = String(seg)
            if (UNSAFE_KEYS.has(key)) return undefined
            cur = (cur as Record<string, unknown>)[key]
        }
    }
    return cur
}

function setAtPath(obj: Record<string, unknown>, path: string, value: unknown): void {
    const segments = flowYamlUtils.parsePath(path)
    if (segments.length === 0) return
    let cur: unknown = obj
    for (let i = 0; i < segments.length - 1; i++) {
        const seg = segments[i]
        if (cur == null || typeof cur !== "object") return
        if (Array.isArray(cur)) {
            cur = (cur as unknown[])[Number(seg)]
        } else {
            const key = String(seg)
            if (UNSAFE_KEYS.has(key)) return
            cur = (cur as Record<string, unknown>)[key]
        }
    }
    const last = segments[segments.length - 1]
    if (cur == null || typeof cur !== "object") return
    if (Array.isArray(cur)) {
        (cur as unknown[])[Number(last)] = value
    } else {
        const key = String(last)
        if (UNSAFE_KEYS.has(key)) return
        (cur as Record<string, unknown>)[key] = value
    }
}

export function buildMinimalTask(fqcn: string, existingIds?: Set<string>): Record<string, unknown> {
    const parts = fqcn.split(".")
    const shortName = parts[parts.length - 1] ?? "task"
    const baseId = shortName.toLowerCase().replace(/[^a-z0-9]+/g, "_") || "task"
    const id = existingIds ? nextAvailableId(baseId, existingIds) : baseId
    return {id, type: fqcn}
}

function nextAvailableId(baseId: string, existingIds: Set<string>): string {
    if (!existingIds.has(baseId)) return baseId
    let counter = 1
    while (existingIds.has(`${baseId}_${counter}`)) counter++
    return `${baseId}_${counter}`
}

export function isWrapperLane(source: string, parentPath: string): boolean {
    try {
        const parsed = flowYamlUtils.parse<Record<string, unknown>>(source)
        if (!parsed) return false
        const list = getAtPath(parsed, parentPath)
        if (Array.isArray(list) && list.length > 0) return isWrappedLaneItem(list[0])

        const lastDot = parentPath.lastIndexOf(".")
        if (lastDot === -1) return false
        const parentBlockPath = parentPath.slice(0, lastDot)
        const parentBlock = getAtPath(parsed, parentBlockPath) as Record<string, unknown> | undefined
        return String(parentBlock?.type ?? "").endsWith(".Dag")
    } catch {
        return false
    }
}

export function wrapAsDagTask(task: Record<string, unknown>): Record<string, unknown> {
    return {task}
}

export function groupValidationIssuesByTask(
    errors: string[] | undefined,
    flow?: Record<string, unknown>,
): Map<string, string[]> {
    const grouped = new Map<string, string[]>()
    const add = (id: string, entry: string) => {
        const existing = grouped.get(id) ?? []
        existing.push(entry)
        grouped.set(id, existing)
    }
    const lines = (errors ?? []).flatMap(raw => raw.split(/[\r\n]+/))
    for (const line of lines) {
        const cleaned = line.replace(/^\s*validation error\s*:\s*/i, "").trim()
        if (!cleaned) continue

        const idMatch = /^([A-Za-z0-9_-]+)(?:\.([A-Za-z0-9_.[\]-]+))?\s*:\s*(.+)$/.exec(cleaned)
        if (idMatch) {
            const [, id, field, message] = idMatch
            add(id, field ? `${field}: ${message.trim()}` : message.trim())
            continue
        }

        const pathMatch = /^(.+\])(?:\.([A-Za-z0-9_]+))?\s*:\s*(.+)$/.exec(cleaned)
        if (!pathMatch || !flow) continue
        const [, rawPath, field, message] = pathMatch
        const taskPath = rawPath.replace(/^_/, "")
        const item = getAtPath(flow, taskPath)
        if (!item || typeof item !== "object") continue
        const id = displayTaskOf(item as Record<string, unknown>).id
        if (id == null) continue
        add(String(id), field ? `${field}: ${message.trim()}` : message.trim())
    }
    return grouped
}

export {collectAllIds}
