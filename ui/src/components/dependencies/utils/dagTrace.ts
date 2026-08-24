export interface TraceEdge {
    source: string;
    target: string;
}

export interface Trace {
    /** The node itself plus every transitive ancestor and descendant. */
    nodes: Set<string>;
    /** Keys of the edges joining those nodes, from `traceEdgeKey`. */
    edges: Set<string>;
}

/** Stable key for an edge. NUL separates so an id containing the separator cannot collide. */
export const traceEdgeKey = (source: string, target: string): string => `${source}\0${target}`

/**
 * Everything upstream and downstream of one node, transitively. Null when there is nothing.
 *
 * `opaque` marks nodes the walk may reach but must not pass through. Flow nodes are opaque:
 * one flow writes many unrelated assets, so traversing it would join every asset it touches
 * into one chain and light the whole graph from any starting point.
 */
export function computeTrace(
    edges: TraceEdge[],
    id?: string,
    opaque?: (id: string) => boolean,
): Trace | null {
    if (!id) return null

    const outgoing = new Map<string, string[]>()
    const incoming = new Map<string, string[]>()
    for (const {source, target} of edges) {
        if (source === target) continue
        if (!outgoing.has(source)) outgoing.set(source, [])
        if (!incoming.has(target)) incoming.set(target, [])
        outgoing.get(source)!.push(target)
        incoming.get(target)!.push(source)
    }

    const nodes = new Set<string>([id])
    const traced = new Set<string>()

    // A visited set per direction, not one shared: sharing lets a node found downstream block
    // the upstream walk through it, hiding genuine ancestors in a cyclic graph.
    const walk = (adjacency: Map<string, string[]>, forward: boolean): void => {
        const visited = new Set<string>([id])
        const queue = [id]
        while (queue.length) {
            const current = queue.shift()!
            for (const next of adjacency.get(current) ?? []) {
                traced.add(forward ? traceEdgeKey(current, next) : traceEdgeKey(next, current))
                nodes.add(next)
                if (visited.has(next)) continue
                visited.add(next)
                // Reached, not entered: an opaque node is an endpoint of the trace.
                if (!opaque?.(next)) queue.push(next)
            }
        }
    }

    walk(outgoing, true)
    walk(incoming, false)

    return {nodes, edges: traced}
}
