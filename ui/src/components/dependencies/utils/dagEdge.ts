import {MarkerType} from "@vue-flow/core"
import {edgeKindToken} from "./relationKind"

export interface DagEdgeState {
    onPath: boolean;
    dimmed: boolean;
    backwards: boolean;
}

export function dagEdgeStyle(
    edge: {kind?: string; directed?: boolean},
    {onPath, dimmed, backwards}: DagEdgeState,
    resolve: (token: string) => string,
) {
    const stroke = resolve(edgeKindToken(edge.kind) ?? (onPath ? "--ks-text-link" : "--ks-border-default"))

    return {
        markerEnd: edge.directed === false ? undefined : {type: MarkerType.ArrowClosed, color: stroke},
        style: {
            stroke,
            strokeWidth: onPath ? 2 : 1,
            strokeDasharray: backwards ? "6 4" : undefined,
            opacity: dimmed ? 0.4 : backwards && !onPath ? 0.65 : 1,
        },
    }
}
