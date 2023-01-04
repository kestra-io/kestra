import {useVueFlow} from "@vue-flow/core"


export const predecessorsEdge = (vueFlowId, nodeUid) => {
    const {getEdges} = useVueFlow({id: vueFlowId});

    let nodes = [];

    for (const edge of getEdges.value) {
        if (edge.target === nodeUid) {
            nodes.push(edge)
            let recursiveEdge = predecessorsEdge(vueFlowId, edge.source);
            if (recursiveEdge.length > 0) {
                nodes.push(...recursiveEdge);
            }
        }
    }

    return nodes;
}

export const successorsEdge = (vueFlowId, nodeUid) => {
    const {getEdges} = useVueFlow({id: vueFlowId});

    let nodes = [];

    for (const edge of getEdges.value) {
        if (edge.source === nodeUid) {
            nodes.push(edge)
            let recursiveEdge = successorsEdge(vueFlowId, edge.target);
            if (recursiveEdge.length > 0) {
                nodes.push(...recursiveEdge);
            }
        }
    }

    return nodes;
}

export const predecessorsNode = (vueFlowId, nodeUid) => {
    const {getEdges, findNode} = useVueFlow({id: vueFlowId});

    let nodes = [findNode(nodeUid)];

    for (const edge of getEdges.value) {
        if (edge.target === nodeUid) {
            nodes.push(edge.sourceNode)
            let recursiveEdge = predecessorsNode(vueFlowId, edge.source);
            if (recursiveEdge.length > 0) {
                nodes.push(...recursiveEdge);
            }
        }
    }

    return nodes;
}

export const successorsNode = (vueFlowId, nodeUid) => {
    const {getEdges, findNode} = useVueFlow({id: vueFlowId});

    let nodes = [findNode(nodeUid)];

    for (const edge of getEdges.value) {
        if (edge.source === nodeUid) {
            nodes.push(edge.targetNode)
            let recursiveEdge = successorsNode(vueFlowId, edge.target);
            if (recursiveEdge.length > 0) {
                nodes.push(...recursiveEdge);
            }
        }
    }

    return nodes;
}

export const linkedElements = (vueFlowId, nodeUid) => {
    return ([
        ...predecessorsEdge(vueFlowId, nodeUid),
        ...predecessorsNode(vueFlowId, nodeUid),
        ...successorsEdge(vueFlowId, nodeUid),
        ...successorsNode(vueFlowId, nodeUid),
    ])
}
