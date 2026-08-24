export interface DagEdge {
    source: string;
    target: string;
}

export interface DagPosition {
    x: number;
    y: number;
}

export interface DagLayout {
    positions: Map<string, DagPosition>;
}

const COLUMN_GAP = 320
const ROW_GAP = 96

/**
 * Ranks every node by longest path from a root into left-to-right columns. Ties break by id,
 * so the same graph always yields the same coordinates, unlike the force layout.
 */
export function computeDagLayout(
    nodeIDs: string[],
    edges: DagEdge[],
    options: {
        columnGap?: number;
        rowGap?: number;
        /** Group index per node, so members of one group sit adjacent within their rank. */
        priority?: (id: string) => number;
        /** Nodes placed in their own leading column, ahead of the ranked ones. */
        ownColumn?: (id: string) => boolean;
    } = {},
): DagLayout {
    const columnGap = options.columnGap ?? COLUMN_GAP
    const rowGap = options.rowGap ?? ROW_GAP
    const priority = options.priority ?? (() => 0)

    const ids = [...new Set(nodeIDs)].sort()
    const known = new Set(ids)

    const links = [
        ...new Map(
            edges
                .filter((edge) => known.has(edge.source) && known.has(edge.target) && edge.source !== edge.target)
                .map((edge) => [`${edge.source} ${edge.target}`, edge]),
        ).values(),
    ]

    const predecessors = new Map<string, string[]>(ids.map((id) => [id, []]))
    const successors = new Map<string, string[]>(ids.map((id) => [id, []]))
    links.forEach(({source, target}) => {
        predecessors.get(target)!.push(source)
        successors.get(source)!.push(target)
    })

    const rank = new Map<string, number>()
    const unresolved = new Map(ids.map((id) => [id, predecessors.get(id)!.length]))

    let pending = ids
    while (pending.length) {
        // A cycle leaves every pending node waiting on a predecessor; cut it at the lowest id
        // rather than stalling, so cyclic graphs still lay out.
        const ready = pending.filter((id) => unresolved.get(id) === 0)
        const wave = ready.length ? ready : [pending[0]]

        wave.forEach((id) => {
            const ranked = predecessors.get(id)!.filter((parent) => rank.has(parent))
            rank.set(id, ranked.length ? Math.max(...ranked.map((parent) => rank.get(parent)!)) + 1 : 0)
        })
        wave.forEach((id) => {
            successors.get(id)!.forEach((next) => unresolved.set(next, Math.max(0, unresolved.get(next)! - 1)))
        })

        pending = pending.filter((id) => !rank.has(id))
    }

    const depth = Math.max(0, ...rank.values()) + 1
    const columns: string[][] = Array.from({length: depth}, () => [])
    ids.forEach((id) => columns[rank.get(id)!].push(id))

    // Barycenter ordering keeps edges short; the id tie-break keeps it stable.
    const order = new Map<string, number>()
    const barycenter = (id: string): number => {
        const placed = predecessors.get(id)!.filter((parent) => order.has(parent))
        if (!placed.length) return Number.MAX_SAFE_INTEGER
        return placed.reduce((sum, parent) => sum + order.get(parent)!, 0) / placed.length
    }

    columns.forEach((column, index) => {
        const sorted = index === 0
            ? [...column].sort((a, b) => priority(a) - priority(b) || (a < b ? -1 : 1))
            : [...column].sort((a, b) => priority(a) - priority(b) || barycenter(a) - barycenter(b) || (a < b ? -1 : 1))
        sorted.forEach((id, position) => order.set(id, position))
        columns[index] = sorted
    })

    // Nodes that trigger the graph rather than sit in it get a column of their own, ahead of
    // everything else.
    const pinned = options.ownColumn ? ids.filter(options.ownColumn) : []
    if (pinned.length && pinned.length < ids.length) {
        const pinnedSet = new Set(pinned)
        const remaining = columns
            .map((column) => column.filter((id) => !pinnedSet.has(id)))
            .filter((column) => column.length)
        columns.length = 0
        columns.push([...pinned].sort((a, b) => priority(a) - priority(b) || (a < b ? -1 : 1)), ...remaining)
    }

    const positions = new Map<string, DagPosition>()
    columns.forEach((column, index) => {
        const offset = (column.length - 1) / 2
        column.forEach((id, position) => {
            positions.set(id, {x: index * columnGap, y: (position - offset) * rowGap})
        })
    })

    return {positions}
}
