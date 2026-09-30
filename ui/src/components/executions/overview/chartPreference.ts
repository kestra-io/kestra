/**
 * Resolution logic for the Execution Overview chart switcher (kestra-ee#11414): which of
 * Topology / Gantt / Logs shows by default, and the per-flow persistence backing it.
 */

/** Node count above which the adaptive default switches from Topology to Gantt. A placeholder,
 *  not a researched value — kept as a single named constant so it is trivial to tune later. */
export const CHART_NODE_THRESHOLD = 40

export type OverviewChart = "topology" | "gantt" | "logs"

export interface FlowRef {
    namespace: string
    flowId: string
}

export interface ChartResolution {
    chart: OverviewChart
    /** True only when the size-based rule picked Gantt over the Topology default — the one case the notice applies to. */
    adaptiveRuleFired: boolean
}

/**
 * Resolution order: a stored per-flow choice always wins; otherwise a flow with more nodes than
 * {@link CHART_NODE_THRESHOLD} defaults to Gantt (a dense graph reads better as a timeline),
 * everything else defaults to Topology.
 */
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

/**
 * A localStorage-backed map keyed on `namespace/flowId`, capped at {@link MAX_STORED_FLOWS}
 * entries (least-recently-set evicted first) so it cannot grow unbounded across many flows.
 */
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
