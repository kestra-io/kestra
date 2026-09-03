export const NODE = "NODE" as const
export const EDGE = "EDGE" as const

export const FLOW = "FLOW" as const
export const EXECUTION = "EXECUTION" as const
export const NAMESPACE = "NAMESPACE" as const
export const ASSET = "ASSET" as const

export type Types = typeof FLOW | typeof EXECUTION | typeof NAMESPACE | typeof ASSET;

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
    /** Source system of the asset, e.g. `bigquery`. */
    system?: string;
    /** Warehouse schema, e.g. a BigQuery dataset. */
    schema?: string;
    /** ISO timestamp of the last lineage event. */
    updated?: string;
    /** Asset type FQCN, e.g. `io.kestra.plugin.ee.assets.Table`; display sites show the trailing segment. */
    assetType?: string;
    /** Task type FQCN of whatever last wrote the asset; resolves the plugin icon and the grouping key. */
    producer?: string;
    /** Freshness against the producing flow's schedule: fresh, stale, failed, unknown. */
    status?: string;
    /** Most recent runs that wrote the asset, newest first. */
    runs?: AssetRun[];
};

export type AssetRun = {
    executionId?: string;
    namespace?: string;
    flowId?: string;
    created?: string;
    state?: string;
};

export type Node = {
    id: string;
    type: "NODE";
    flow: string;
    /** Absent on asset nodes; the other three subtypes always carry one. */
    namespace?: string;
    metadata: Flow | Execution | Namespace | Asset;
};

export type Edge = {
    id: string;
    type: "EDGE";
    source: string;
    target: string;
};

export type Element = { data: Node } | { data: Edge };

export const nodesOf = (elements: Element[]): Node[] =>
    elements.filter((el): el is {data: Node} => el.data.type === NODE).map(({data}) => data)

export const edgesOf = (elements: Element[]): Edge[] =>
    elements.filter((el): el is {data: Edge} => el.data.type === EDGE).map(({data}) => data)
