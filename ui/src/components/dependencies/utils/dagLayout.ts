interface DagEdge {
    source: string;
    target: string;
}

/** Deterministic left-to-right layout: ranks by longest path from a root and returns the centre position per node id. */
export function computeDagLayout(
    nodeIDs: string[],
    edges: DagEdge[],
    options: {
        columnGap: number;
        rowGap: number;
        /** Group index per node, so members of one group sit adjacent within their rank. */
        priority?: (id: string) => number;
        /** Nodes placed in their own leading column, ahead of the ranked ones. */
        ownColumn?: (id: string) => boolean;
    },
): Map<string, {x: number; y: number}> {
    const {columnGap, rowGap} = options
    const priority = options.priority ?? (() => 0)

    const byPriority = (a: string, b: string): number => priority(a) - priority(b)
    const byID = (a: string, b: string): number => (a < b ? -1 : 1)

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
        // A cycle leaves every pending node waiting on a predecessor; cut it at the lowest id rather than stalling.
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
    let columns: string[][] = Array.from({length: depth}, () => [])
    ids.forEach((id) => columns[rank.get(id)!].push(id))

    const order = new Map<string, number>()
    const barycenter = (id: string): number => {
        const placed = predecessors.get(id)!.filter((parent) => order.has(parent))
        if (!placed.length) {
            return Number.MAX_SAFE_INTEGER
        }
        return placed.reduce((sum, parent) => sum + order.get(parent)!, 0) / placed.length
    }

    columns.forEach((column, index) => {
        const sorted = index === 0
            ? [...column].sort((a, b) => byPriority(a, b) || byID(a, b))
            : [...column].sort((a, b) => byPriority(a, b) || barycenter(a) - barycenter(b) || byID(a, b))
        sorted.forEach((id, position) => order.set(id, position))
        columns[index] = sorted
    })

    const pinned = options.ownColumn ? ids.filter(options.ownColumn) : []
    if (pinned.length && pinned.length < ids.length) {
        const pinnedSet = new Set(pinned)
        const remaining = columns
            .map((column) => column.filter((id) => !pinnedSet.has(id)))
            .filter((column) => column.length)
        columns = [[...pinned].sort((a, b) => byPriority(a, b) || byID(a, b)), ...remaining]
    }

    const positions = new Map<string, {x: number; y: number}>()
    columns.forEach((column, index) => {
        const offset = (column.length - 1) / 2
        column.forEach((id, position) => {
            positions.set(id, {x: index * columnGap, y: (position - offset) * rowGap})
        })
    })

    return positions
}
