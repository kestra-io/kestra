import type {LocationQuery} from "vue-router"
import {decodeFilterValue, parseFilterKey} from "@kestra-io/design-system"
import type {LogicalOperator} from "@kestra-io/design-system"
import type {QueryFilter, QueryFilterField, QueryFilterLogical, QueryFilterOp} from "@kestra-io/kestra-sdk"

const QUERY_FILTER_LOGICAL: Record<LogicalOperator, QueryFilterLogical> = {AND: "and", OR: "or"}

/**
 * Builds a `QueryFilter` group node. TExecutions.tshe backend rejects a node that also carries a
 * `field`/`operation`/`value` (see `QueryFilter`'s canonical constructor), so a group is only ever
 * `logical` + `children`, with `logical` in the lowercase form its enum accepts.
 */
const queryFilterNode = (logical: LogicalOperator, children: QueryFilter[]): QueryFilter =>
    ({logical: QUERY_FILTER_LOGICAL[logical], children})

/**
 * Builds one logical position in the filter tree, mirroring the backend's
 * `QueryFilterFormatBinder.NodeBuilder`: direct leaves land here directly, LABELS leaves sharing an
 * operation are merged into one filter with a map value, and nested `[and|or][N]` segments descend
 * into a sub-node keyed by (logical, index) that is recursively flattened by {@link build}.
 */
class FilterNodeBuilder {
    private readonly directLeaves: QueryFilter[] = []
    private readonly labelsByOp = new Map<QueryFilterOp, Record<string, string>>()
    private readonly subNodes = new Map<LogicalOperator, Map<number, FilterNodeBuilder>>()

    descend(logical: LogicalOperator, index: number): FilterNodeBuilder {
        const slots = this.subNodes.get(logical) ?? new Map<number, FilterNodeBuilder>()
        this.subNodes.set(logical, slots)
        const slot = slots.get(index) ?? new FilterNodeBuilder()
        slots.set(index, slot)
        return slot
    }

    addLeaf(field: QueryFilterField, operation: QueryFilterOp, subKey: string | undefined, value: string | string[]) {
        const scalarValue = Array.isArray(value) ? value[0] : value

        if (field === "labels" && subKey) {
            const map = this.labelsByOp.get(operation) ?? {}
            map[subKey] = scalarValue
            this.labelsByOp.set(operation, map)
            return
        }

        this.directLeaves.push({field, operation, value})
    }

    build(): QueryFilter[] {
        const items: QueryFilter[] = [...this.directLeaves]

        this.labelsByOp.forEach((map, operation) => {
            if (Object.keys(map).length > 0) {
                items.push({field: "labels", operation, value: map})
            }
        })

        this.subNodes.forEach((slots, logical) => {
            const branches: QueryFilter[] = []
            slots.forEach((slot) => {
                const slotItems = slot.build()
                if (slotItems.length === 0) return
                branches.push(slotItems.length === 1
                    ? slotItems[0]
                    : queryFilterNode("AND", slotItems))
            })
            if (branches.length === 0) return
            items.push(branches.length === 1 && logical === "AND"
                ? branches[0]
                : queryFilterNode(logical, branches))
        })

        return items
    }
}

/**
 * Converts a route's raw `filters[...]` query params (as produced by the design system's
 * `encodeFiltersToQuery` / `encodeFilterGroupsToQuery`) into the `QueryFilter[]` array shape the
 * generated `@kestra-io/kestra-sdk` search functions expect. This lives in the app, not in the
 * design system: the design system owns the URL key format (and exposes `parseFilterKey` for it),
 * while the backend payload shape is the app's business.
 *
 * Unlike `decodeSearchParams` — which targets restoring `KsFilter` UI chips and lossily flattens a
 * labels `subKey` into a `"key:value"` string — this preserves the LABELS sub-key structure and
 * nested AND/OR grouping needed for a faithful backend request, by porting
 * `QueryFilterFormatBinder`'s tree-building.
 */
export const routeQueryToQueryFilters = (query: LocationQuery): QueryFilter[] => {
    const root = new FilterNodeBuilder()

    for (const [key, value] of Object.entries(query)) {
        if (!key.startsWith("filters[") || !value) continue
        const parsed = parseFilterKey(key)
        if (!parsed) continue

        let target = root
        for (const segment of parsed.chain) {
            target = target.descend(segment.logical, segment.index)
        }
        target.addLeaf(
            parsed.field as QueryFilterField,
            parsed.operation as QueryFilterOp,
            parsed.subKey,
            decodeFilterValue(value) as string | string[],
        )
    }

    return root.build()
}

/**
 * Keeps only the top-level `filters[...]` params of a route query that sit on one of `fields`.
 *
 * Use it to narrow a filter dropdown's own value lookup by the filters already applied to the list.
 * Because top-level filter params are ANDed together, forwarding a subset of them can only loosen
 * the lookup - so it never hides a value the list itself would show. That is also why members of a
 * nested AND/OR group are never forwarded: dropping one disjunct of an OR would tighten the lookup
 * instead, and could hide values the list shows.
 *
 * Pagination and sort params are dropped for free: they are not `filters[...]` keys.
 */
export const keepTopLevelFilters = (query: LocationQuery, fields: string[]): LocationQuery =>
    Object.fromEntries(
        Object.entries(query).filter(([key, value]) => {
            if (!key.startsWith("filters[") || !value) return false

            const parsed = parseFilterKey(key)
            return parsed !== null && parsed.chain.length === 0 && fields.includes(parsed.field)
        }),
    )
