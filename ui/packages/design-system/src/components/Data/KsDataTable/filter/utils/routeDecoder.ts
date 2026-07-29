/**
 * Pure URL-query → FilterGroup[] decoder. Extracted from useFilters because the pipeline is
 * a closed transformation: input is a vue-router LocationQuery, output is the tree shape the
 * chip UI renders. Nothing here touches Vue refs, the router, or the chip state.
 *
 * Three named passes, in order:
 *   1. `bucketParams`      — sort decoded params by (topIdx, wrapperChildIdx) into Slot maps.
 *   2. `buildLeafFromSlot` — turn one Slot into a LeafFilterGroup.
 *   3. `assembleUnits`     — fold the bucket map into top-level units (leaves or wrappers).
 *
 * Date fields (startDate/endDate/nextExecutionDate/…) are ordinary fields here: a relative duration
 * decodes as a single `time-range` chip, and an absolute lower+upper pair merges into a range chip.
 */
import type {LocationQuery} from "vue-router"
import {
    type FilterConfiguration,
    type FilterGroup,
    type LeafFilterGroup,
    type LogicalOperator,
    type WrapperGroup,
    type AppliedFilter,
    Comparators,
} from "./filterTypes"
import {type DecodedParam, decodeSearchParams} from "./helpers"
import {newGroupId} from "../composables/useFilterGroups"
import {createAppliedFilter, createCustomRangeFilter, processFieldValue} from "./filterChipFactory"

/** A bag of params bucketed into one logical position in the tree. */
type Slot = {
    fieldParams: Map<string, DecodedParam[]>;
}

type BucketedParams = {
    perTop: Map<number, {isWrapper: boolean; children: Map<number, Slot>}>;
    observedTopLogical: LogicalOperator | undefined;
    wrapperLogicalByTopIdx: Map<number, LogicalOperator>;
}

const emptySlot = (): Slot => ({fieldParams: new Map()})

/**
 * Sort decoded URL params into buckets keyed by (topIdx, wrapperChildIdx). Tracks the
 * top-level operator and each wrapper's operator as side effects of the first param that
 * specifies them — well-formed URLs are consistent so the first wins.
 */
const bucketParams = (params: DecodedParam[]): BucketedParams => {
    const perTop = new Map<number, {isWrapper: boolean; children: Map<number, Slot>}>()
    let observedTopLogical: LogicalOperator | undefined
    const wrapperLogicalByTopIdx = new Map<number, LogicalOperator>()

    const getSlot = (topIdx: number, wrapperChildIdx?: number): Slot => {
        if (!perTop.has(topIdx)) {
            perTop.set(topIdx, {isWrapper: false, children: new Map()})
        }
        const top = perTop.get(topIdx)!
        if (wrapperChildIdx !== undefined) top.isWrapper = true
        const childKey = wrapperChildIdx ?? -1
        if (!top.children.has(childKey)) top.children.set(childKey, emptySlot())
        return top.children.get(childKey)!
    }

    params.forEach(param => {
        const topIdx = param.groupIndex ?? 0
        if (param.topLogical && observedTopLogical === undefined) {
            observedTopLogical = param.topLogical
        }
        if (param.wrapperLogical && !wrapperLogicalByTopIdx.has(topIdx)) {
            wrapperLogicalByTopIdx.set(topIdx, param.wrapperLogical)
        }
        const slot = getSlot(topIdx, param.wrapperChildIndex)
        // Bucket by (field, operation) so same-field/different-comparator pairs survive.
        const bucketKey = `${param.field}|${param.operation ?? ""}`
        if (!slot.fieldParams.has(bucketKey)) slot.fieldParams.set(bucketKey, [])
        slot.fieldParams.get(bucketKey)!.push(param)
    })

    return {perTop, observedTopLogical, wrapperLogicalByTopIdx}
}

/** Build a single LeafFilterGroup from one bucketed Slot. */
const buildLeafFromSlot = (
    slot: Slot,
    configuration: FilterConfiguration,
): LeafFilterGroup => {
    const filtersMap = new Map<string, AppliedFilter>()

    // Pre-pass: a `time-range` field in range mode arrives as two buckets (GTE + LTE) because
    // bucketParams keys by `field|operation`. Merge them into one range chip and mark the buckets
    // consumed so the normal pass below doesn't render them as two separate `>=` / `<=` chips.
    const consumedBuckets = new Set<string>()
    const paramsByField = new Map<string, DecodedParam[]>()
    slot.fieldParams.forEach((params, bucketKey) => {
        const field = params[0]?.field ?? bucketKey.split("|")[0]
        if (!paramsByField.has(field)) paramsByField.set(field, [])
        paramsByField.get(field)!.push(...params)
    })
    paramsByField.forEach((params, field) => {
        const config = configuration.keys?.find(k => k?.key === field)
        if (config?.valueType !== "time-range") return
        const gte = params.find(p => p.operation === "GREATER_THAN_OR_EQUAL_TO")
        const lte = params.find(p => p.operation === "LESS_THAN_OR_EQUAL_TO")
        if (!gte || !lte) return
        filtersMap.set(
            `${field}|GREATER_THAN_OR_EQUAL_TO`,
            createCustomRangeFilter(field, config, new Date(gte.value as string), new Date(lte.value as string)),
        )
        consumedBuckets.add(`${field}|GREATER_THAN_OR_EQUAL_TO`)
        consumedBuckets.add(`${field}|LESS_THAN_OR_EQUAL_TO`)
    })

    slot.fieldParams.forEach((params, bucketKey) => {
        if (consumedBuckets.has(bucketKey)) return
        const field = params[0]?.field ?? bucketKey.split("|")[0]
        const config = configuration.keys?.find(k => k?.key === field)
        if (!config) return

        const parsedComparator = Comparators[params[0]?.operation as keyof typeof Comparators]
        const comparator = config.comparators?.includes(parsedComparator) ? parsedComparator : undefined
        if (!comparator) return

        const {value, valueLabel} = processFieldValue(config, params, comparator)
        filtersMap.set(
            `${field}|${params[0]?.operation ?? ""}`,
            createAppliedFilter(field, config, comparator, value, valueLabel, params[0]?.operation),
        )
    })

    return {id: newGroupId(), kind: "leaf", filters: Array.from(filtersMap.values())}
}

/** Fold the bucket map into a sorted list of top-level FilterGroup units. */
const assembleUnits = (
    bucketed: BucketedParams,
    configuration: FilterConfiguration,
): FilterGroup[] => {
    const orderedTop = Array.from(bucketed.perTop.entries()).sort(([a], [b]) => a - b)
    return orderedTop.flatMap(([topIdx, top]): FilterGroup[] => {
        if (!top.isWrapper) {
            const slot = top.children.get(-1) ?? emptySlot()
            const leaf = buildLeafFromSlot(slot, configuration)
            return leaf.filters.length > 0 ? [leaf] : []
        }
        const orderedChildren = Array.from(top.children.entries())
            .filter(([k]) => k >= 0)
            .sort(([a], [b]) => a - b)
        const childLeaves = orderedChildren
            .map(([, slot]) => buildLeafFromSlot(slot, configuration))
            .filter(c => c.filters.length > 0)
        if (childLeaves.length === 0) return []
        if (childLeaves.length === 1) return [childLeaves[0]]
        const wrapper: WrapperGroup = {
            id: newGroupId(),
            kind: "wrapper",
            logical: bucketed.wrapperLogicalByTopIdx.get(topIdx) ?? "AND",
            children: childLeaves,
        }
        return [wrapper]
    })
}

/**
 * Top-level entry: decode a route query into the chip-tree shape plus the observed top-level
 * operator. Returns `{groups, topLogical}` so callers can sync both at once.
 */
export const parseEncodedGroups = (
    routeQuery: LocationQuery,
    configuration: FilterConfiguration,
): {groups: FilterGroup[]; topLogical: LogicalOperator} => {
    const bucketed = bucketParams(decodeSearchParams(routeQuery))
    const groups = assembleUnits(bucketed, configuration)
    return {groups, topLogical: bucketed.observedTopLogical ?? "OR"}
}
