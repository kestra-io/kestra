
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

const FAILED_LIKE_STATES = new Set(["FAILED", "KILLED", "KILLING", "WARNING"])

export function isFailedLikeState(state: string): boolean {
    return FAILED_LIKE_STATES.has(state)
}

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

function slotResolution(rangeSpanMs: number, availableWidthPx: number): {slotCount: number; slotSpanMs: number} {
    const slotCount = Math.max(1, Math.floor(availableWidthPx / MIN_DISCRETE_BAR_WIDTH_PX))
    return {slotCount, slotSpanMs: rangeSpanMs / slotCount}
}

function slotIndexFor(startMs: number, rangeStartMs: number, rangeEndMs: number, slotCount: number, slotSpanMs: number): number {
    const anchor = Math.min(Math.max(startMs, rangeStartMs), rangeEndMs - 1)
    return Math.min(slotCount - 1, Math.floor((anchor - rangeStartMs) / slotSpanMs))
}

export function shouldBucketRow(
    executions: TimelineExecution[],
    rangeStartMs: number,
    rangeEndMs: number,
    availableWidthPx: number,
): boolean {
    const span = rangeEndMs - rangeStartMs
    if (executions.length <= 0 || availableWidthPx <= 0 || span <= 0) return false

    const {slotCount, slotSpanMs} = slotResolution(span, availableWidthPx)
    const occupiedSlots = new Set<number>()

    for (const execution of executions) {
        const slot = slotIndexFor(execution.startMs, rangeStartMs, rangeEndMs, slotCount, slotSpanMs)
        if (occupiedSlots.has(slot)) return true
        occupiedSlots.add(slot)
    }

    return false
}

function fillBuckets(
    executions: TimelineExecution[],
    rangeStartMs: number,
    rangeEndMs: number,
    bucketCount: number,
    bucketSpanMs: number,
): StateBucket[] {
    const buckets: StateBucket[] = Array.from({length: bucketCount}, (_, i) => ({
        startMs: rangeStartMs + i * bucketSpanMs,
        endMs: rangeStartMs + (i + 1) * bucketSpanMs,
        total: 0,
        byState: {},
        dominantState: "",
    }))

    for (const execution of executions) {
        const index = slotIndexFor(execution.startMs, rangeStartMs, rangeEndMs, bucketCount, bucketSpanMs)
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

    return buckets
}

export function bucketize(
    executions: TimelineExecution[],
    rangeStartMs: number,
    rangeEndMs: number,
    availableWidthPx: number,
): StateBucket[] {
    const span = rangeEndMs - rangeStartMs
    if (span <= 0 || availableWidthPx <= 0) return []

    const {slotCount, slotSpanMs} = slotResolution(span, availableWidthPx)
    return fillBuckets(executions, rangeStartMs, rangeEndMs, slotCount, slotSpanMs).filter(bucket => bucket.total > 0)
}

export function densitySeries(
    executions: TimelineExecution[],
    rangeStartMs: number,
    rangeEndMs: number,
    bucketCount: number,
): StateBucket[] {
    const span = rangeEndMs - rangeStartMs
    if (span <= 0 || bucketCount <= 0) return []

    return fillBuckets(executions, rangeStartMs, rangeEndMs, bucketCount, span / bucketCount)
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

const DAY_MS = 86_400_000

export function axisTickFormat(rangeSpanMs: number, tickCount: number): string {
    if (rangeSpanMs / tickCount < 60_000) return "LTS"
    if (rangeSpanMs < DAY_MS) return "LT"
    if (rangeSpanMs < 7 * DAY_MS) return "MMM D, LT"
    return "MMM D"
}

export function buildAxisTicks(rangeStartMs: number, rangeEndMs: number, tickCount: number, nowMs: number): AxisTick[] {
    const span = rangeEndMs - rangeStartMs
    if (span <= 0) return []

    const isPinnedToNow = nowMs - rangeEndMs < 60_000
    return Array.from({length: tickCount + 1}, (_, i) => ({
        ms: rangeStartMs + (span * i) / tickCount,
        isNow: i === tickCount && isPinnedToNow,
    }))
}
