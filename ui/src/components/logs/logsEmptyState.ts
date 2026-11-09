import type {LocationQueryRaw} from "vue-router"
import {readRouteLevelFilter} from "@kestra-io/design-system"
import type {LevelFilterValue} from "@kestra-io/design-system"
import {FILTER_FIELD_PATTERN} from "../executions/utils"

export const isDefaultLogLevel = (level: LevelFilterValue | undefined, defaultLevel: string): boolean =>
    level === undefined || (level.direction === "min" && level.value === defaultLevel)

export function hasUserLogFilters(
    query: LocationQueryRaw,
    options: {defaultLevel: string; scopeFields?: readonly string[]},
): boolean {
    const scopeFields = new Set(["level", ...(options.scopeFields ?? [])])
    const hasNonLevelFilter = Object.keys(query).some((key) => {
        const field = key.match(FILTER_FIELD_PATTERN)?.[1]
        return field !== undefined && !scopeFields.has(field)
    })
    return hasNonLevelFilter || !isDefaultLogLevel(readRouteLevelFilter(query), options.defaultLevel)
}
