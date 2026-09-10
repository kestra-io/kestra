/**
 * Pure grouping, lane-packing and level-of-detail helpers for the Executions timeline view.
 * Kept framework-free so drill-down grouping, lane assignment and the bucketing threshold can be
 * unit tested without mounting any component.
 */

export interface TimelineExecution {
    id: string;
    namespace: string;
    flowId: string;
    state: string;
    startMs: number;
    endMs: number;
}

export interface TimelineExecutionWithLane extends TimelineExecution {
    lane: number;
}

export interface NamespaceGroup {
    namespace: string;
    total: number;
    failed: number;
    flows: FlowGroup[];
}

export interface FlowGroup {
    namespace: string;
    flowId: string;
    total: number;
    failed: number;
    executions: TimelineExecution[];
}

export interface StateBucket {
    startMs: number;
    endMs: number;
    total: number;
    byState: Record<string, number>;
    dominantState: string;
}

// Failed-ish states counted in the per-namespace/flow failure counters shown next to each row label.
const FAILED_LIKE_STATES = new Set(["FAILED", "KILLED", "KILLING", "WARNING"])

export function isFailedLikeState(state: string): boolean {
    return FAILED_LIKE_STATES.has(state)
}

/**
 * Groups a flat execution list into namespace rows with nested flow rows, each carrying the total
 * and failed-like counts derived from the same fetched list (no extra request).
 */
export function groupByNamespace(executions: TimelineExecution[]): NamespaceGroup[] {
    const namespaces = new Map<string, Map<string, TimelineExecution[]>>()

    for (const execution of executions) {
        let flows = namespaces.get(execution.namespace)
        if (!flows) {
            flows = new Map()
            namespaces.set(execution.namespace, flows)
        }
        const bucket = flows.get(execution.flowId) ?? []
        bucket.push(execution)
        flows.set(execution.flowId, bucket)
    }

    return [...namespaces.entries()]
        .map(([namespace, flows]) => {
            const flowGroups: FlowGroup[] = [...flows.entries()]
                .map(([flowId, flowExecutions]) => ({
                    namespace,
                    flowId,
                    total: flowExecutions.length,
                    failed: flowExecutions.filter(e => isFailedLikeState(e.state)).length,
                    executions: flowExecutions,
                }))
                .sort((a, b) => b.total - a.total)

            return {
                namespace,
                total: flowGroups.reduce((sum, f) => sum + f.total, 0),
                failed: flowGroups.reduce((sum, f) => sum + f.failed, 0),
                flows: flowGroups,
            }
        })
        .sort((a, b) => b.total - a.total)
}

/**
 * Packs overlapping executions of a single flow onto separate lanes (classic greedy interval
 * scheduling), so concurrent/drifting runs render as stacked lanes instead of collapsing visually.
 */
export function assignLanes(executions: TimelineExecution[]): TimelineExecutionWithLane[] {
    const sorted = [...executions].sort((a, b) => a.startMs - b.startMs)
    const laneEndMs: number[] = []
    const result: TimelineExecutionWithLane[] = []

    for (const execution of sorted) {
        let lane = laneEndMs.findIndex(end => end <= execution.startMs)
        if (lane === -1) {
            lane = laneEndMs.length
            laneEndMs.push(execution.endMs)
        } else {
            laneEndMs[lane] = execution.endMs
        }
        result.push({...execution, lane})
    }

    return result
}

export const MIN_DISCRETE_BAR_WIDTH_PX = 3

/**
 * A row switches from individual bars to aggregated density buckets once the available pixel width
 * can no longer give each execution at least {@link MIN_DISCRETE_BAR_WIDTH_PX} of its own room.
 * Evaluated per row/lane rather than globally, so a busy namespace can bucket while a quiet one in
 * the same view still renders discrete bars.
 */
export function shouldBucketRow(executionCount: number, availableWidthPx: number): boolean {
    if (executionCount <= 0 || availableWidthPx <= 0) return false
    return availableWidthPx / executionCount < MIN_DISCRETE_BAR_WIDTH_PX
}

/**
 * Aggregates executions into fixed-width time buckets across [rangeStartMs, rangeEndMs), one bucket
 * per {@link MIN_DISCRETE_BAR_WIDTH_PX} of available width. A bucket only ever reports counts by
 * state, never an execution id, so its tooltip cannot promise identifying a single run.
 */
export function bucketize(
    executions: TimelineExecution[],
    rangeStartMs: number,
    rangeEndMs: number,
    availableWidthPx: number,
): StateBucket[] {
    const span = rangeEndMs - rangeStartMs
    if (span <= 0 || availableWidthPx <= 0) return []

    const bucketCount = Math.max(1, Math.floor(availableWidthPx / MIN_DISCRETE_BAR_WIDTH_PX))
    const bucketSpanMs = span / bucketCount
    const buckets: StateBucket[] = Array.from({length: bucketCount}, (_, i) => ({
        startMs: rangeStartMs + i * bucketSpanMs,
        endMs: rangeStartMs + (i + 1) * bucketSpanMs,
        total: 0,
        byState: {},
        dominantState: "",
    }))

    for (const execution of executions) {
        const anchor = Math.min(Math.max(execution.startMs, rangeStartMs), rangeEndMs - 1)
        const index = Math.min(bucketCount - 1, Math.floor((anchor - rangeStartMs) / bucketSpanMs))
        const bucket = buckets[index]
        bucket.total += 1
        bucket.byState[execution.state] = (bucket.byState[execution.state] ?? 0) + 1
    }

    for (const bucket of buckets) {
        let max = 0
        for (const [state, count] of Object.entries(bucket.byState)) {
            if (count > max) {
                max = count
                bucket.dominantState = state
            }
        }
    }

    return buckets.filter(bucket => bucket.total > 0)
}

export interface StateCount {
    state: string;
    count: number;
}

export function countByState(executions: TimelineExecution[]): StateCount[] {
    const counts = new Map<string, number>()
    for (const execution of executions) {
        counts.set(execution.state, (counts.get(execution.state) ?? 0) + 1)
    }
    return [...counts.entries()]
        .map(([state, count]) => ({state, count}))
        .sort((a, b) => b.count - a.count)
}

export interface AxisTick {
    ms: number;
    isNow: boolean;
}

/**
 * Evenly spaced tick timestamps across [rangeStartMs, rangeEndMs], `tickCount + 1` points including
 * both ends. The last tick is flagged {@link AxisTick.isNow} instead of carrying a timestamp label
 * once the range's end is pinned to "now" (within a minute), so the caller can render "Now" there.
 */
export function buildAxisTicks(rangeStartMs: number, rangeEndMs: number, tickCount: number, nowMs: number): AxisTick[] {
    const span = rangeEndMs - rangeStartMs
    if (span <= 0) return []

    const isPinnedToNow = nowMs - rangeEndMs < 60_000
    return Array.from({length: tickCount + 1}, (_, i) => ({
        ms: rangeStartMs + (span * i) / tickCount,
        isNow: i === tickCount && isPinnedToNow,
    }))
}
