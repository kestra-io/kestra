import {useVueFlow} from "@vue-flow/core"
import type {Edge, Node} from "@vue-flow/core"

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

export const predecessorsNode = (vueFlowId: string, nodeUid: string): (Node | undefined)[] => {
    const {getEdges, findNode} = useVueFlow(vueFlowId)

    const nodes: (Node | undefined)[] = [findNode(nodeUid)]

    for (const edge of getEdges.value) {
        if (edge.target === nodeUid) {
            nodes.push(edge.sourceNode)
            const recursiveEdge = predecessorsNode(vueFlowId, edge.source)
            if (recursiveEdge.length > 0) {
                nodes.push(...recursiveEdge)
            }
        }
    }

    return nodes
}

export const successorsNode = (vueFlowId: string, nodeUid: string): (Node | undefined)[] => {
    const {getEdges, findNode} = useVueFlow(vueFlowId)

    const nodes: (Node | undefined)[] = [findNode(nodeUid)]

    for (const edge of getEdges.value) {
        if (edge.source === nodeUid) {
            nodes.push(edge.targetNode)
            const recursiveEdge = successorsNode(vueFlowId, edge.target)
            if (recursiveEdge.length > 0) {
                nodes.push(...recursiveEdge)
            }
        }
    }

    return nodes
}

export const linkedElements = (vueFlowId: string, nodeUid: string): (Edge | Node | undefined)[] => {
    return ([
        ...predecessorsEdge(vueFlowId, nodeUid),
        ...predecessorsNode(vueFlowId, nodeUid),
        ...successorsEdge(vueFlowId, nodeUid),
        ...successorsNode(vueFlowId, nodeUid),
    ])
}
