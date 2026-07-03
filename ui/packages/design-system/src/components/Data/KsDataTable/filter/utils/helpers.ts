import type {LocationQuery} from "vue-router"
import {type AppliedFilter, type FilterGroup, type LeafFilterGroup, type LogicalOperator, Comparators, isWrapperGroup} from "./filterTypes"
import {MAX_RENDERABLE_NESTING_DEPTH} from "./constants"

const decodeURIComponentSafely = (value: string | (string | null)[]): string | string[] =>
    Array.isArray(value)
        ? value.filter(v => v !== null).map(decodeURIComponent)
        : decodeURIComponent(value)

export function getComparator(comparatorKey: keyof typeof Comparators): Comparators {
    return Comparators[comparatorKey]
}

export function keyOfComparator(comparator: Comparators): keyof typeof Comparators {
    return Object.entries(Comparators).find(([_, value]) => value === comparator)![0] as keyof typeof Comparators
}

/**
 * Single unified regex matching every filter URL shape the chip UI supports.
 * Group 1 captures the optional `[and|or][N]` prefix chain (0 to MAX_RENDERABLE_NESTING_DEPTH pairs).
 * Groups 2/3/4 are field / operation / optional subKey.
 *
 * Keys with more prefix pairs than the chip UI can render fail to match and fall through to null;
 * `isUnrenderableFilterKey` flags them separately so the UI can switch to the raw editor.
 */
const FILTER_KEY_PATTERN = new RegExp(
    `^filters((?:\\[(?:and|or)]\\[\\d+]){0,${MAX_RENDERABLE_NESTING_DEPTH}})\\[([^\\]]*?)]\\[([^\\]]*?)](?:\\[(.*?)])?$`,
    "i",
)

const PREFIX_SEGMENT_PATTERN = /\[(and|or)]\[(\d+)]/gi

interface PrefixSegment {
    logical: LogicalOperator
    index: number
}

const parsePrefixChain = (prefix: string): PrefixSegment[] => {
    if (!prefix) return []
    const result: PrefixSegment[] = []
    let match: RegExpExecArray | null
    PREFIX_SEGMENT_PATTERN.lastIndex = 0
    while ((match = PREFIX_SEGMENT_PATTERN.exec(prefix)) !== null) {
        result.push({logical: match[1].toUpperCase() as LogicalOperator, index: Number(match[2])})
    }
    return result
}

export interface DecodedParam {
    field: string
    value: string | string[]
    operation: string
    groupIndex?: number
    wrapperChildIndex?: number
    topLogical?: LogicalOperator
    wrapperLogical?: LogicalOperator
}


export const decodeSearchParams = (query: LocationQuery): DecodedParam[] =>
    Object.entries(query)
        .filter(([key]) => key.startsWith("filters[") || key === "q")
        .map(([key, value]): DecodedParam | null => {
            if (!value) return null
            const match = key.match(FILTER_KEY_PATTERN)
            if (!match) return null

            const [, prefix, field, operation, subKey] = match
            const chain = parsePrefixChain(prefix)
            return buildParam(field, operation, subKey, value, chain)
        })
        .filter((v): v is DecodedParam => v !== null)

const buildParam = (
    field: string,
    operation: string,
    subKey: string | undefined,
    value: string | (string | null)[],
    chain: PrefixSegment[],
): DecodedParam => {
    const decoded = subKey
        ? `${subKey}:${decodeURIComponentSafely(value)}`
        : decodeURIComponentSafely(value)
    return {
        field,
        value: decoded,
        operation,
        ...(chain[0] !== undefined ? {groupIndex: chain[0].index, topLogical: chain[0].logical} : {}),
        ...(chain[1] !== undefined ? {wrapperChildIndex: chain[1].index, wrapperLogical: chain[1].logical} : {}),
    }
}

type Filter = Pick<AppliedFilter, "key" | "comparator" | "value">;

type ComparatorKeyResolver = (comparator: Comparators) => string;

export const encodeFiltersToQuery = (filters: Filter[], getComparatorKey: ComparatorKeyResolver) =>
    encodeFilterGroupsToQuery(
        filters.length > 0 ? [{id: "0", filters: filters as AppliedFilter[]}] : [],
        getComparatorKey,
    )

export const encodeFilterGroupsToQuery = (
    groups: FilterGroup[],
    getComparatorKey: ComparatorKeyResolver,
    topLogical: LogicalOperator = "OR",
): Record<string, string> => {
    const query: Record<string, string> = {}
    const onlyOneLeaf = groups.length === 1 && !isWrapperGroup(groups[0])
    const topOp = topLogical.toLowerCase()

    groups.forEach((unit, unitIdx) => {
        const outerPrefix = onlyOneLeaf ? "filters" : `filters[${topOp}][${unitIdx}]`

        if (isWrapperGroup(unit)) {
            const wrapperOp = unit.logical.toLowerCase()
            unit.children.forEach((child, childIdx) => {
                const innerPrefix = `${outerPrefix}[${wrapperOp}][${childIdx}]`
                child.filters.forEach(filter => writeFilter(query, innerPrefix, filter, getComparatorKey))
            })
        } else {
            (unit as LeafFilterGroup).filters.forEach(filter =>
                writeFilter(query, outerPrefix, filter, getComparatorKey))
        }
    })

    return query
}

const writeFilter = (
    query: Record<string, string>,
    prefix: string,
    filter: Filter,
    getComparatorKey: ComparatorKeyResolver,
) => {
    const {key, comparator, value} = filter
    const comparatorKey = getComparatorKey(comparator)

    switch (key) {
        case "timeRange": {
            if (typeof value === "object" && "startDate" in value) {
                query["filters[startDate][GREATER_THAN_OR_EQUAL_TO]"] = value.startDate.toISOString()
                query["filters[endDate][LESS_THAN_OR_EQUAL_TO]"] = value.endDate.toISOString()
            } else {
                query[`filters[${key}][${comparatorKey}]`] = value?.toString() ?? ""
            }
            const dateFilter = (filter as any).meta?.dateFilter
            if (dateFilter) {
                query["dateFilter"] = dateFilter
            }
            return
        }
        default: {
            // A `time-range` field in custom range mode: encode {startDate,endDate} as a GTE/LTE pair
            // on the field's own key (works inside [and|or][N] group prefixes too). The dedicated
            // `timeRange` key keeps its own startDate/endDate encoding via the case above.
            if (value && typeof value === "object" && "startDate" in value && "endDate" in value) {
                const {startDate, endDate} = value as {startDate: Date; endDate: Date}
                query[`${prefix}[${key}][GREATER_THAN_OR_EQUAL_TO]`] = startDate.toISOString()
                query[`${prefix}[${key}][LESS_THAN_OR_EQUAL_TO]`] = endDate.toISOString()
            } else if (Array.isArray(value) && value.some(v => typeof v === "string" && v.includes(":"))) {
                value.forEach((item: string) => {
                    const [k, v] = item.split(":", 2)
                    if (k && v) query[`${prefix}[${key}][${comparatorKey}][${k}]`] = v
                })
            } else {
                query[`${prefix}[${key}][${comparatorKey}]`] = Array.isArray(value)
                    ? value.join(",")
                    : value instanceof Date
                        ? value.toISOString()
                        : value?.toString() ?? ""
            }
        }
    }
}

export const isValidFilter = (filter: Filter): boolean => {
    const {value} = filter

    if (value == null || value === "") return false

    switch (true) {
        case Array.isArray(value):
            return value.length > 0
        case typeof value === "object" && "startDate" in value:
            return !!(value.startDate && value.endDate)
        case value instanceof Date:
            return true
        default:
            return true
    }
}

export const validStructureSignature = (groups: FilterGroup[]): string => {
    const leafFilters = (leaf: LeafFilterGroup): string[] =>
        leaf.filters
            .filter(isValidFilter)
            .map((f) => `${f.key} ${f.comparator} ${JSON.stringify(f.value)}`)
            .sort()

    const units = groups
        .map((unit) => {
            if (isWrapperGroup(unit)) {
                const children = unit.children.map(leafFilters).filter((c) => c.length > 0)
                if (children.length === 0) return null
                if (children.length === 1) return {filters: children[0]}
                return {logical: unit.logical, children}
            }
            const filters = leafFilters(unit as LeafFilterGroup)
            return filters.length ? {filters} : null
        })
        .filter(Boolean)

    return JSON.stringify(units)
}

export const getUniqueFilters = <T extends { key: string; comparator?: any }>(filters: T[]): T[] =>
    filters.filter((filter, index, self) =>
        index === self.findLastIndex(f =>
            f.key === filter.key && f.comparator === filter.comparator,
        ),
    )

export const clearFilterQueryParams = (query: Record<string, any>): void => {
    for (const key of Object.keys(query)) {
        if (key.startsWith("filters[") || key === "dateFilter") delete query[key]
    }
}

/**
 * Returns true if a `filters[...]` key has more `[and|or][N]` prefix segments than the chip UI
 * can render. The chip UI supports a top-level group plus a single wrapper inside it
 * ({@link MAX_RENDERABLE_NESTING_DEPTH} segments total). Anything with a wrapper inside a wrapper
 * — or any further nesting — needs the raw editor.
 */
export const isUnrenderableFilterKey = (key: string): boolean => {
    if (!key.startsWith("filters[")) return false
    const matches = key.match(/\[(?:and|or)]\[\d+]/gi)
    return (matches?.length ?? 0) > MAX_RENDERABLE_NESTING_DEPTH
}

export const findUnrenderableFilterKeys = (query: LocationQuery): string[] =>
    Object.keys(query).filter(isUnrenderableFilterKey)

export const serializeFiltersToString = (query: LocationQuery): string => {
    const lines: string[] = []
    Object.entries(query).forEach(([key, value]) => {
        if (!key.startsWith("filters[")) return
        const append = (raw: string) => lines.push(`${key}=${raw}`)
        if (Array.isArray(value)) {
            value.forEach(v => v != null && append(String(v)))
        } else if (value != null) {
            append(String(value))
        }
    })
    return lines.join("&")
}

const safeDecode = (s: string): string => {
    try { return decodeURIComponent(s) } catch { return s }
}

export const parseFiltersFromString = (str: string): Record<string, string | string[]> => {
    const result: Record<string, string | string[]> = {}
    // Split on newlines primarily; tolerate `&` between lines for users pasting URL queries.
    str.split(/\r?\n/).flatMap(line => line.split(/&(?=filters[[%])/)).forEach(line => {
        const trimmed = line.trim()
        if (!trimmed) return
        const eqIdx = trimmed.indexOf("=")
        if (eqIdx < 0) return
        const key = safeDecode(trimmed.slice(0, eqIdx))
        const value = safeDecode(trimmed.slice(eqIdx + 1))
        if (!key.startsWith("filters[")) return
        const existing = result[key]
        if (existing === undefined) {
            result[key] = value
        } else if (Array.isArray(existing)) {
            existing.push(value)
        } else {
            result[key] = [existing, value]
        }
    })
    return result
}

export const isSearchPath = (name: string) =>
    ["home", "flows/list", "executions/list", "logs/list", "admin/triggers"].includes(name)