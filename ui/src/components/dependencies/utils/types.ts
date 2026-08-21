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
    /** Source system of the asset, e.g. `bigquery`. Coalesced from `system` or `provider`. */
    system?: string;
    /** Warehouse schema, e.g. a BigQuery dataset. Set by every plugin emitting a Table. */
    schema?: string;
    /** ISO timestamp of the last lineage event, shown as the node age in DAG view. */
    updated?: string;
    /**
     * Asset type FQCN, e.g. `io.kestra.plugin.ee.assets.Table`. Whole value is stored;
     * display sites show the trailing segment (`Table`, `VM`).
     */
    assetType?: string;
    /**
     * Task type FQCN of whatever last wrote the asset, e.g. `io.kestra.plugin.dbt.cli.DbtCLI`.
     * Whole value resolves the plugin icon; its fourth segment is the grouping key.
     */
    producer?: string;
    /** Freshness against the producing flow's schedule: fresh, stale, failed, never, unknown. */
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
