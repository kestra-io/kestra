import type {LocationQuery} from "vue-router"
import {decodeFilterValue, parseFilterKey} from "@kestra-io/design-system"
import type {LogicalOperator} from "@kestra-io/design-system"
import type {QueryFilter, QueryFilterField, QueryFilterLogical, QueryFilterOp} from "@kestra-io/kestra-sdk"

const QUERY_FILTER_LOGICAL: Record<LogicalOperator, QueryFilterLogical> = {AND: "and", OR: "or"}

/** A group node is only ever `logical` + `children`: the backend rejects one that also carries a field. */
const queryFilterNode =(logical: LogicalOperator, children: QueryFilter[]): QueryFilter =>
    ({logical: QUERY_FILTER_LOGICAL[logical], children})

/** One logical position in the filter tree, mirroring the backend's `QueryFilterFormatBinder.NodeBuilder`. */
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
 * Converts a route's raw `filters[...]` query params into the `QueryFilter[]` shape the SDK search
 * functions expect.
 *
 * Not `decodeSearchParams`, which targets `KsFilter` UI chips and lossily flattens a labels `subKey`
 * into a `"key:value"` string: this preserves the sub-key structure and the AND/OR nesting.
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
