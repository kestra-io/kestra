import * as flowYamlUtils from "@kestra-io/topology/flow-yaml-utils"
import {
    addBlockAtPath,
    deleteBlockAtPath,
    displayTaskOf,
    healDagRemoval,
    isWrapperLane,
    rewireDagDependency,
    wrapAsDagTask,
    type BlockSection,
    type DagDependency,
} from "../../../utils/flowableBlockOps"

type Translate = (key: string, named?: Record<string, unknown>) => string

export const ALL_SECTIONS: BlockSection[] = ["tasks", "triggers", "errors", "finally", "afterExecution"]

/** Sections that hold tasks. `triggers` is excluded: a flow may legally reuse a trigger id for a task
 *  (FlowValidator checks task ids and trigger ids as two separate sets), so searching it when resolving
 *  a task would route the insertion into `triggers:`. */
export const TASK_SECTIONS: BlockSection[] = ["tasks", "errors", "finally", "afterExecution"]

const SECTION_SENTINEL_PREFIX = "__section:"
const LANE_SENTINEL_PREFIX = "__lane:"

export const NESTED_BLOCK_KEYS = ["tasks", "then", "else", "finally", "errors", "defaults"]

export function sectionDisplayLabel(t: Translate, section: BlockSection): string {
    if (section === "triggers") return t("no_code.sections.triggers")
    if (section === "errors") return t("block_editor.lane_errors")
    if (section === "finally") return t("block_editor.lane_finally")
    if (section === "afterExecution") return t("no_code.sections.afterExecution")
    return t("no_code.sections.tasks")
}

export function sectionSentinelId(section: BlockSection): string {
    return `${SECTION_SENTINEL_PREFIX}${section}`
}

export function sectionFromSentinel(id: string | undefined): BlockSection | undefined {
    if (!id?.startsWith(SECTION_SENTINEL_PREFIX)) return undefined
    const section = id.slice(SECTION_SENTINEL_PREFIX.length) as BlockSection
    return ALL_SECTIONS.includes(section) ? section : undefined
}

export function parentPathFromLaneSentinel(id: string | undefined): string | undefined {
    if (!id?.startsWith(LANE_SENTINEL_PREFIX)) return undefined
    return id.slice(LANE_SENTINEL_PREFIX.length)
}

export function laneDisplayLabelFromPath(t: Translate, parentPath: string): string {
    const casesMatch = parentPath.match(/\.cases\.([^.]+)$/)
    if (casesMatch) return t("block_editor.lane_case", {key: casesMatch[1]})
    const laneName = parentPath.slice(parentPath.lastIndexOf(".") + 1)
    if (laneName === "then") return t("block_editor.lane_then")
    if (laneName === "else") return t("block_editor.lane_else")
    if (laneName === "errors") return t("block_editor.lane_errors")
    if (laneName === "finally") return t("block_editor.lane_finally")
    if (laneName === "defaults") return t("block_editor.lane_defaults")
    if (laneName === "tasks") return t("block_editor.lane_tasks")
    return laneName.toUpperCase()
}

const TASK_LIST_LANE_KEYS = new Set([...ALL_SECTIONS, ...NESTED_BLOCK_KEYS])

/** True for a bare root section such as `tasks`, false for a nested lane such as `tasks[0].then`. */
export function isRootSectionPath(parentPath: string): boolean {
    return (ALL_SECTIONS as string[]).includes(parentPath)
}

/** Tells a lane holding tasks apart from any other array a schema-driven form renders, such as the flow inputs. */
export function isTaskListPath(parentPath: string): boolean {
    if (/\.cases[.[]/.test(parentPath)) return true
    return TASK_LIST_LANE_KEYS.has(parentPath.split(".").pop() ?? "")
}

export function sectionFromParentPath(parentPath: string): BlockSection {
    const lane = parentPath.split(".").pop() ?? ""
    if (lane === "errors") return "errors"
    if (lane === "finally") return "finally"
    if (lane === "afterExecution") return "afterExecution"
    if (lane === "triggers") return "triggers"
    return "tasks"
}

/**
 * Resolves an existing task's id to where a sibling would be inserted next to it: the array
 * holding it and its index there. Handles a task wrapped in a lane item (e.g. a Dag's `{task: ...}`)
 * by resolving to the wrapper's position in its array, since a sibling is inserted next to the
 * wrapper, not inside it.
 */
export function resolveTaskInsertionTarget(
    source: string,
    section: BlockSection,
    taskId: string,
): {parentPath: string; refIndex: number} | undefined {
    const path = flowYamlUtils.getPathFromSectionAndId({source, section, id: taskId})
    if (!path) return undefined

    const parsedPath = flowYamlUtils.parsePath(path)
    const refIndex = parsedPath.findLast((p): p is number => typeof p === "number")
    if (refIndex === undefined) return undefined

    const fieldNameAny = parsedPath[parsedPath.length - 1]
    const fieldName = typeof fieldNameAny === "string" ? fieldNameAny : undefined

    const refLength = refIndex.toString().length + 2 + (fieldName ? fieldName.length + 1 : 0)
    const parentPath = path.slice(0, -refLength)

    return {parentPath, refIndex}
}

/**
 * Resolves a task id whose section is unknown: the topology's edge `+` only carries the neighbouring
 * task's id, so an `errors` or `finally` lane resolved against `tasks` would silently find nothing.
 * Task ids are unique across the sections that hold tasks, so the first match is the right one.
 */
export function resolveTaskInsertionTargetInAnySection(
    source: string,
    taskId: string,
): {parentPath: string; refIndex: number; section: BlockSection} | undefined {
    for (const section of TASK_SECTIONS) {
        const target = resolveTaskInsertionTarget(source, section, taskId)
        if (target) return {...target, section}
    }
    return undefined
}

export function findNestedPath(items: Record<string, unknown>[], id: string, prefix: string): string | undefined {
    for (let index = 0; index < items.length; index++) {
        const item = items[index]
        if (!item || typeof item !== "object") continue
        const path = `${prefix}[${index}]`
        if (String(item.id) === id) return path
        for (const key of NESTED_BLOCK_KEYS) {
            const branch = item[key]
            if (Array.isArray(branch)) {
                const found = findNestedPath(branch as Record<string, unknown>[], id, `${path}.${key}`)
                if (found) return found
            }
        }
        const cases = item.cases
        if (cases && typeof cases === "object" && !Array.isArray(cases)) {
            for (const caseKey of Object.keys(cases as Record<string, unknown>)) {
                const branch = (cases as Record<string, unknown>)[caseKey]
                if (Array.isArray(branch)) {
                    const found = findNestedPath(branch as Record<string, unknown>[], id, flowYamlUtils.appendKeyToPath(`${path}.cases`, caseKey))
                    if (found) return found
                }
            }
        }
    }
    return undefined
}

export interface MoveTarget {
    refId: string
    position: "before" | "after"
    dagDependency?: DagDependency
}

/**
 * Moves an existing task onto the edge `target` describes, which is the drag counterpart of the
 * edge `+`: a sequential lane gets a reorder, a Dag gets its `dependsOn` chain rewired. The
 * destination index is resolved after the removal so a same-lane move cannot shift onto itself.
 */
export function moveTaskOntoEdge(source: string, movedId: string, target: MoveTarget): string {
    // An edge the task is already an endpoint of describes where it already is, and re-inserting it
    // there would only cost it the `dependsOn` that is stripped on the way out.
    if (movedId === target.refId) return source
    if (target.dagDependency?.fromId === movedId || target.dagDependency?.toId === movedId) return source

    const origin = resolveTaskInsertionTargetInAnySection(source, movedId)
    if (!origin) return source

    const originPath = `${origin.parentPath}[${origin.refIndex}]`
    const movedBlock = flowYamlUtils.extractBlockWithPath({source, path: originPath})
    if (!movedBlock) return source

    const healed = isWrapperLane(source, origin.parentPath)
        ? healDagRemoval(source, origin.parentPath, movedId)
        : source
    const withoutMoved = deleteBlockAtPath(healed, `${origin.parentPath}[${origin.refIndex}]`)

    const destination = resolveTaskInsertionTargetInAnySection(withoutMoved, target.refId)
    if (!destination) return source

    const parsedBlock = flowYamlUtils.parse<Record<string, unknown>>(movedBlock)
    if (!parsedBlock) return source
    const task = displayTaskOf(parsedBlock)
    // `dependsOn` only means something in the lane it came from, so it is dropped on the way out.
    const bare = isWrapperLane(withoutMoved, destination.parentPath) ? wrapAsDagTask(task) : task

    const inserted = addBlockAtPath(
        withoutMoved,
        destination.parentPath,
        bare,
        destination.refIndex,
        target.position,
    )

    return target.dagDependency
        ? rewireDagDependency(inserted, destination.parentPath, movedId, target.dagDependency)
        : inserted
}
