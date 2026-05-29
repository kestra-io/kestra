import type {
    LocationQuery,
    LocationQueryRaw,
    LocationQueryValue,
    LocationQueryValueRaw,
} from "vue-router"
import type {AppliedFilter} from "./filterTypes"
import {Comparators} from "./filterTypes"

const LEVEL_FILTER_PREFIX = "filters[level]["
const LEVEL_GTE_FILTER_KEY = "filters[level][GREATER_THAN_OR_EQUAL_TO]"
const LEVEL_LTE_FILTER_KEY = "filters[level][LESS_THAN_OR_EQUAL_TO]"
const LEVEL_EQUALS_FILTER_KEY = "filters[level][EQUALS]"
const LEGACY_LEVEL_FILTER_KEY = "level"

const SUPPORTED_LEVEL_FILTER_KEYS = new Set([
    LEVEL_GTE_FILTER_KEY,
    LEVEL_LTE_FILTER_KEY,
    LEVEL_EQUALS_FILTER_KEY,
])

export type LevelFilterDirection = "min" | "max";

export interface LevelFilterValue {
    value: string;
    direction: LevelFilterDirection;
}

const firstStringValue = (
    value:
        | LocationQueryValue
        | LocationQueryValueRaw
        | (LocationQueryValue | LocationQueryValueRaw)[]
        | undefined,
) => {
    if (Array.isArray(value)) {
        return typeof value[0] === "string" ? value[0] : undefined
    }

    return typeof value === "string" ? value : undefined
}

const nonEmpty = (value: string | undefined) =>
    value && value.length > 0 ? value : undefined

export const readRouteLevelFilter = (query: LocationQuery | LocationQueryRaw): LevelFilterValue | undefined => {
    const lte = nonEmpty(firstStringValue(query[LEVEL_LTE_FILTER_KEY]))
    if (lte) {
        return {value: lte, direction: "max"}
    }

    const gte = nonEmpty(firstStringValue(query[LEVEL_GTE_FILTER_KEY]))
    if (gte) {
        return {value: gte, direction: "min"}
    }

    // Legacy: EQUALS (pre-rename) and bare `level` query param both meant "at or above"
    const legacyEquals = nonEmpty(firstStringValue(query[LEVEL_EQUALS_FILTER_KEY]))
    if (legacyEquals) {
        return {value: legacyEquals, direction: "min"}
    }

    const legacyLevel = nonEmpty(firstStringValue(query[LEGACY_LEVEL_FILTER_KEY]))
    if (legacyLevel) {
        return {value: legacyLevel, direction: "min"}
    }

    return undefined
}

export const hasUnsupportedRouteLevelComparator = (query: LocationQuery | LocationQueryRaw) =>
    Object.keys(query).some(
        (key) =>
            key === LEGACY_LEVEL_FILTER_KEY ||
            (key.startsWith(LEVEL_FILTER_PREFIX) && !SUPPORTED_LEVEL_FILTER_KEYS.has(key)),
    )

export const readAppliedLevelFilter = (filters: AppliedFilter[]): LevelFilterValue | undefined => {
    const levelFilter = filters.find((filter) => filter.key === "level")
    if (!levelFilter) {
        return undefined
    }

    const direction: LevelFilterDirection =
        levelFilter.comparator === Comparators.LESS_THAN_OR_EQUAL_TO ? "max" : "min"

    const rawValue = Array.isArray(levelFilter.value)
        ? levelFilter.value[0]
        : levelFilter.value

    if (typeof rawValue !== "string" || rawValue.length === 0) {
        return undefined
    }

    return {value: rawValue, direction}
}

export const normalizeRouteLevelFilter = (
    query: Record<string, any>,
    level: LevelFilterValue | string | undefined,
) => {
    const normalized = {...query}

    Object.keys(normalized).forEach((key) => {
        if (key.startsWith(LEVEL_FILTER_PREFIX)) {
            delete normalized[key]
        }
    })

    delete normalized[LEGACY_LEVEL_FILTER_KEY]

    if (level) {
        // Backward-compat: plain string callers default to min (≥) semantic
        const resolved: LevelFilterValue =
            typeof level === "string" ? {value: level, direction: "min"} : level
        const key = resolved.direction === "max" ? LEVEL_LTE_FILTER_KEY : LEVEL_GTE_FILTER_KEY
        normalized[key] = resolved.value
    }

    return normalized
}

export const levelToRequestParams = (
    level: LevelFilterValue | undefined,
): Record<string, string> => {
    if (!level) {
        return {}
    }
    const key = level.direction === "max" ? LEVEL_LTE_FILTER_KEY : LEVEL_GTE_FILTER_KEY
    return {[key]: level.value}
}
