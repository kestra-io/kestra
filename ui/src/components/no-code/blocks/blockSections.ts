import {flowYamlUtils} from "@kestra-io/topology"
import type {BlockSection} from "../../../utils/flowableBlockOps"

type Translate = (key: string, named?: Record<string, unknown>) => string

export const ALL_SECTIONS: BlockSection[] = ["tasks", "triggers", "errors", "finally", "afterExecution"]

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
