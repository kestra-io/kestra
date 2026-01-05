export const NODE = "NODE" as const;
export const EDGE = "EDGE" as const;

export const FLOW = "FLOW" as const;
export const EXECUTION = "EXECUTION" as const;
export const NAMESPACE = "NAMESPACE" as const;
export const ASSET = "ASSET" as const;

type Flow = {
    subtype: typeof FLOW;
};

type Execution = {
    subtype: typeof EXECUTION;
    id?: string;
    state?: string;
};

type Namespace = {
    subtype: typeof NAMESPACE;
};

type Asset = {
    subtype: typeof ASSET;
};

export type Node = {
    id: string;
    type: "NODE";
    flow: string;
    namespace: string;
    metadata: Flow | Execution | Namespace | Asset;
};

export type Edge = {
    id: string;
    type: "EDGE";
    source: string;
    target: string;
};

export type Element = { data: Node } | { data: Edge };

export type States = {
    default: string;
    faded: string;
    selected: string;
    hovered: string;
    assets: string;
};
