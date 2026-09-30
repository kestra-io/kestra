import {isTaskNode, type MinimalNode} from "@kestra-io/topology/vue-flow-utils"

// Placeholder, not a researched value: no instrumentation on topology graph size exists yet.
export const CHART_NODE_THRESHOLD = 40

export type OverviewChart = "topology" | "gantt" | "logs"

export interface FlowRef {
    namespace: string
    flowId: string
}

export interface ChartResolution {
    chart: OverviewChart
    adaptiveRuleFired: boolean
}

// The graph's raw node list includes the synthetic GraphClusterRoot/GraphClusterEnd nodes, which
// would otherwise inflate both the notice copy and the adaptive threshold.
export function countTaskNodes(nodes: MinimalNode[] | undefined): number {
    return nodes?.filter(isTaskNode).length ?? 0
}

export function resolveOverviewChart(
    storedChart: OverviewChart | undefined,
    nodeCount: number,
    threshold: number = CHART_NODE_THRESHOLD,
): ChartResolution {
    if (storedChart) {
        return {chart: storedChart, adaptiveRuleFired: false}
    }

    if (nodeCount > threshold) {
        return {chart: "gantt", adaptiveRuleFired: true}
    }

    return {chart: "topology", adaptiveRuleFired: false}
}

const MAX_STORED_FLOWS = 50

function flowStorageKey({namespace, flowId}: FlowRef): string {
    return `${namespace}/${flowId}`
}

export class BoundedFlowStore<V> {
    constructor(
        private readonly storageKey: string,
        private readonly capacity: number = MAX_STORED_FLOWS,
    ) {}

    get(flow: FlowRef): V | undefined {
        return this.read().get(flowStorageKey(flow))
    }

    set(flow: FlowRef, value: V): void {
        const map = this.read()
        const key = flowStorageKey(flow)
        map.delete(key)
        map.set(key, value)
        while (map.size > this.capacity) {
            const oldest = map.keys().next().value
            if (oldest === undefined) break
            map.delete(oldest)
        }
        this.write(map)
    }

    private read(): Map<string, V> {
        try {
            const raw = localStorage.getItem(this.storageKey)
            if (!raw) return new Map()
            return new Map(JSON.parse(raw) as [string, V][])
        } catch {
            return new Map()
        }
    }

    private write(map: Map<string, V>): void {
        localStorage.setItem(this.storageKey, JSON.stringify([...map.entries()]))
    }
}

export const chartByFlowStore = new BoundedFlowStore<OverviewChart>("executionOverviewChartByFlow")
export const chartNoticeDismissedByFlowStore = new BoundedFlowStore<boolean>("executionOverviewChartNoticeDismissedByFlow")
