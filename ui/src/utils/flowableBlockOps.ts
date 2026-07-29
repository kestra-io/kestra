import {flowYamlUtils} from "@kestra-io/topology"

export type BlockSection = "tasks" | "triggers" | "errors" | "finally" | "afterExecution"

const FLOWABLE_BRANCH_KEYS = ["tasks", "then", "else", "errors", "finally", "defaults", "cases"] as const

export const FLOWABLE_SUFFIXES = [
    "If", "Switch", "Parallel", "Sequential", "ForEach",
    "EachSequential", "Dag", "WaitFor", "ForEachItem",
] as const

export function isFlowableType(
    type: string,
    icons?: Record<string, {flowable: boolean}>,
): boolean {
    // The type-suffix match is authoritative for the core flow-control tasks:
    // the plugin-icon `flowable` flag is unreliable (a lazily-resolved
    // ecosystem icon carries flowable=false), so it must not demote a known
    // flowable to a plain leaf card. Fall back to the icon flag only for
    // custom flowables whose type doesn't match a known suffix.
    if (FLOWABLE_SUFFIXES.some(suffix => type.endsWith(`.${suffix}`))) {
        return true
    }
    return icons?.[type]?.flowable ?? false
}

// A DAG's `tasks` lane holds `{task, dependsOn}` wrappers instead of flat task
// nodes — every other flowable (If/Switch/Parallel/Sequential/...) keeps flat
// lanes. Detecting the wrapper by shape (a `task` object property, no `type`
// of its own) rather than hardcoding "this only applies to Dag" means any
// future flowable reusing the same wrapper shape is handled for free, while a
// flat lane item (which always carries its own `type`) is never mistaken for one.
export function isWrappedLaneItem(item: unknown): item is {task: Record<string, unknown>; dependsOn?: unknown} {
    if (!item || typeof item !== "object" || Array.isArray(item)) return false
    const obj = item as Record<string, unknown>
    return obj.type === undefined && Boolean(obj.task) && typeof obj.task === "object" && !Array.isArray(obj.task)
}

// The task to actually render/edit for a lane item — unwraps a DAG-style
// `{task, dependsOn}` wrapper, or returns the item itself for a flat lane.
export function displayTaskOf(item: Record<string, unknown>): Record<string, unknown> {
    return isWrappedLaneItem(item) ? item.task : item
}

// Appends `.task` to a lane item's own path when it is a wrapper, so callers
// that need to read/edit/replace the INNER task (not the wrapper) get the
// right path without needing to know about DAG at every call site.
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

// Used as data-block-id for the keyboard-focus ring — normally just the task's
// own id, matching every other place that keys off it (insertTask, moveFocus,
// etc.). Two sibling tasks can share a user-typed id though (e.g. mid-rename),
// which would make both light up together since the reactive comparison is
// per-card, not list-aware — so only a genuine duplicate gets its index
// appended, disambiguating it while leaving the common (unique-id) case,
// and every id-based write-site, untouched. A DAG-style {task, dependsOn}
// wrapper has no id of its own, so it resolves off the wrapped task's id.
// Cached per list instance: the template asks for the same item's dom id 2-3×
// per render (:key, :data-block-id, :focused) across every item, so resolving
// each in isolation would scan the list every time (O(n²) per lane). The lane
// arrays are fresh computed references, so a WeakMap keyed on the array both
// gives one O(n) pass per render and lets stale entries be collected.
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
    // A wrapper (DAG lane item) has no id of its own — the new id comes from
    // its wrapped task, and only that inner task gets renamed/re-ided; the
    // wrapper's own dependsOn is copied as-is (it still refers to valid,
    // untouched sibling ids).
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
        // ignore parse errors
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
    for (const key of FLOWABLE_BRANCH_KEYS) {
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

// Object keys that must never be traversed or written through a string path:
// a task id or Switch case key flows into these path helpers, and letting
// `__proto__`/`constructor`/`prototype` through would risk prototype pollution.
const UNSAFE_KEYS = new Set(["__proto__", "constructor", "prototype"])

// Path parsing is shared with the flow-editor YAML layer so the two never drift
// on how a segment (including a bracket-quoted Switch case key like ["eu.prod"])
// is tokenized.
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

// Whether the lane at parentPath is DAG-style (its items are {task, dependsOn}
// wrappers). Checked primarily by shape against whatever the lane already
// contains — no need to know which flowable kinds use the wrapper shape.
// Falls back to the parent block's own type only for an empty lane (a brand
// new Dag with no tasks yet), where there is no existing item to sniff.
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

// Groups a flow's validation constraint strings by the task id they concern so
// block cards (leaf tasks and flowables alike) can flag their own issues.
// Constraints come in a few shapes, optionally behind a "Validation error: "
// label:
//   - "<id>.<field>: <message>"  a specific field (e.g. "fetch_data.uri: must not be null")
//   - "<id>: <message>"          a task/flowable-level error (e.g. a DAG's "my_dag: Cyclic dependency detected: a, b")
//   - "<path>.<field>: <message>" a path-addressed task in a section the backend
//                                 reports by index rather than id — "errors[0].message",
//                                 "_finally[0].message", "tasks[0].then[1].uri". These
//                                 are resolved back to the task id via the parsed flow
//                                 (leaving errors/finally/afterExecution cards able to
//                                 flag their own issues, not just the tasks section).
// Anything that resolves to no task id (pure flow-level errors) is skipped.
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
    // A single entry can bundle several constraints separated by newlines (the
    // backend joins them that way), so flatten to individual lines first.
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

        // Path-addressed constraint (head carries a "[index]"): resolve the task
        // path to its id through the parsed flow. The path is everything up to the
        // last "]"; an optional trailing ".field" is the field name.
        const pathMatch = /^(.+\])(?:\.([A-Za-z0-9_]+))?\s*:\s*(.+)$/.exec(cleaned)
        if (!pathMatch || !flow) continue
        const [, rawPath, field, message] = pathMatch
        // The backend prefixes the reserved word with an underscore (`_finally`).
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
