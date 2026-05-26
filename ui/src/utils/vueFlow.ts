import {useVueFlow, Edge, GraphNode} from "@vue-flow/core"

export const predecessorsEdge = (vueFlowId: string, nodeUid: string): Edge[] => {
    const {getEdges} = useVueFlow(vueFlowId)

    const nodes: Edge[] = []

    for (const edge of getEdges.value) {
        if (edge.target === nodeUid) {
            nodes.push(edge)
            const recursiveEdge = predecessorsEdge(vueFlowId, edge.source)
            if (recursiveEdge.length > 0) {
                nodes.push(...recursiveEdge)
            }
        }
    }

    return nodes
}

export const successorsEdge = (vueFlowId: string, nodeUid: string): Edge[] => {
    const {getEdges} = useVueFlow(vueFlowId)

    const nodes: Edge[] = []

    for (const edge of getEdges.value) {
        if (edge.source === nodeUid) {
            nodes.push(edge)
            const recursiveEdge = successorsEdge(vueFlowId, edge.target)
            if (recursiveEdge.length > 0) {
                nodes.push(...recursiveEdge)
            }
        }
    }

    return nodes
}

export const predecessorsNode = (vueFlowId: string, nodeUid: string): (GraphNode | undefined)[] => {
    const {getEdges, findNode} = useVueFlow(vueFlowId)

    const nodes: (GraphNode | undefined)[] = [findNode(nodeUid)]

    for (const edge of getEdges.value) {
        if (edge.target === nodeUid) {
            // FIXME: type this properly
            nodes.push((edge as any).sourceNode)
            const recursiveEdge = predecessorsNode(vueFlowId, edge.source)
            if (recursiveEdge.length > 0) {
                nodes.push(...recursiveEdge)
            }
        }
    }

    return nodes
}

export const successorsNode = (vueFlowId: string, nodeUid: string): (GraphNode | undefined)[] => {
    const {getEdges, findNode} = useVueFlow(vueFlowId)

    const nodes: (GraphNode | undefined)[] = [findNode(nodeUid)]

    for (const edge of getEdges.value) {
        if (edge.source === nodeUid) {
            // FIXME: type this properly
            nodes.push((edge as any).targetNode)
            const recursiveEdge = successorsNode(vueFlowId, edge.target)
            if (recursiveEdge.length > 0) {
                nodes.push(...recursiveEdge)
            }
        }
    }

    return nodes
}

export const linkedElements = (vueFlowId: string, nodeUid: string): (Edge | GraphNode | undefined)[] => {
    return ([
        ...predecessorsEdge(vueFlowId, nodeUid),
        ...predecessorsNode(vueFlowId, nodeUid),
        ...successorsEdge(vueFlowId, nodeUid),
        ...successorsNode(vueFlowId, nodeUid),
    ])
}
