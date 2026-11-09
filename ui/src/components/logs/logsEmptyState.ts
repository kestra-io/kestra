import type {LocationQueryRaw} from "vue-router"
import {readRouteLevelFilter} from "@kestra-io/design-system"
import type {LevelFilterValue} from "@kestra-io/design-system"
import {FILTER_FIELD_PATTERN} from "../executions/utils"

export const isDefaultLogLevel = (level: LevelFilterValue | undefined, defaultLevel: string): boolean =>
    level === undefined || (level.direction === "min" && level.value === defaultLevel)

export function hasUserLogFilters(
    query: LocationQueryRaw,
    options: {defaultLevel: string; defaultQuery?: LocationQueryRaw; scopeFields?: readonly string[]},
): boolean {
    const scopeFields = new Set(["level", ...(options.scopeFields ?? [])])
    const defaultQuery = options.defaultQuery ?? {}
    const hasNonLevelFilter = Object.keys(query).some((key) => {
        const field = key.match(FILTER_FIELD_PATTERN)?.[1]
        return field !== undefined
            && !scopeFields.has(field)
            && !(key in defaultQuery && firstValue(query[key]) === firstValue(defaultQuery[key]))
    })
    return hasNonLevelFilter || !isDefaultLogLevel(readRouteLevelFilter(query), options.defaultLevel)
}

const firstValue = (value: LocationQueryRaw[string]) => Array.isArray(value) ? value[0] : value
