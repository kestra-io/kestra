import type {FlowGraph as SDKFlowGraph} from "@kestra-io/kestra-sdk"
import type {FlowGraph} from "@kestra-io/topology/vue-flow-utils"

/**
 * The generated `FlowGraph` leaves every collection optional and types a node as the abstract `AbstractGraph`
 * (`uid`, `type`, `branchType` only), because the OpenAPI schema does not describe the polymorphic node subclasses
 * the backend actually sends. The topology package owns the concrete shape, so this is the single place that
 * states it: it fills in the collections a graph without edges or clusters omits and narrows the abstract nodes.
 */
export function toFlowGraph(graph: SDKFlowGraph): FlowGraph {
    return {
        ...graph,
        nodes: (graph.nodes ?? []) as FlowGraph["nodes"],
        clusters: (graph.clusters ?? []) as FlowGraph["clusters"],
        edges: (graph.edges ?? []) as FlowGraph["edges"],
    }
}
