export const NODE = "NODE" as const;
export const EDGE = "EDGE" as const;

export const FLOW = "FLOW" as const;
export const EXECUTION = "EXECUTION" as const;
export const NAMESPACE = "NAMESPACE" as const;

type Flow = {
    subtype: typeof FLOW;
};

type Execution = {
    subtype: typeof EXECUTION;
    state?: string;
};

type Namespace = {
    subtype: typeof NAMESPACE;
};

export type Node = {
    id: string;
    type: "NODE";
    flow: string;
    namespace: string;
    metadata: Flow | Execution | Namespace;
};

export type Edge = {
    id: string;
    type: "EDGE";
    source: string;
    target: string;
};

export type Element = { data: Node } | { data: Edge };
