export const NODE = "NODE" as const;
export const EDGE = "EDGE" as const;

export const FLOW = "FLOW" as const;
export const EXECUTION = "EXECUTION" as const;

type Flow = {
    subtype: typeof FLOW;
};

type Execution = {
    subtype: typeof EXECUTION;
    state?: string;
};

export type Node = {
    id: string;
    type: "NODE";
    flow: string;
    namespace: string;
    metadata: Flow | Execution;
};

export type Edge = {
    id: string;
    type: "EDGE";
    source: string;
    target: string;
};

export type Element = { data: Node } | { data: Edge };
