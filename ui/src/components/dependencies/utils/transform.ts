// Kept out of useDependencies so the flow store can reach it without importing
// the composable, which imports the flow store back.
import {v4 as uuid} from "uuid"

import {NODE, EDGE} from "./types"
import type {Types, Node, Edge, Element} from "./types"

/**
 * Transforms an API response containing nodes and edges into
 * dependency Element[] with the given subtype.
 */
export function transformResponse(
    response: { nodes: { uid: string; namespace: string; id: string }[]; edges: { source: string; target: string }[] },
    subtype: Types,
): Element[] {
    const nodes: Node[] = response.nodes.map((node) => ({
        id: node.uid,
        type: NODE,
        flow: node.id,
        namespace: node.namespace,
        metadata: {subtype},
    }))
    const edges: Edge[] = response.edges.map((edge) => ({
        id: uuid(),
        type: EDGE,
        source: edge.source,
        target: edge.target,
    }))

    return [
        ...nodes.map((node) => ({data: node}) as Element),
        ...edges.map((edge) => ({data: edge}) as Element),
    ]
}
