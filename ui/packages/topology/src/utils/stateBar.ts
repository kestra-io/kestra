import type {StateCounts} from "./loopOutcome"

export interface StateBarSegment {
    state: string;
    count: number;
    color: string;
    notStarted: boolean;
}

export interface StateBarOptions {
    total?: number;
    running?: number;
}

export const NOT_STARTED = "NOT_STARTED"

const RUNNING_STATE = "RUNNING"

const NEUTRAL_COLOR = "var(--ks-status-neutral)"

const NOT_STARTED_COLOR = "color-mix(in srgb, var(--ks-status-neutral) 30%, transparent)"

const ORDERED_STATE_COLORS: ReadonlyArray<readonly [string, string]> = [
    ["SUCCESS", "var(--ks-status-success)"],
    ["WARNING", "var(--ks-status-warning)"],
    ["FAILED", "var(--ks-status-failed)"],
    ["KILLED", "var(--ks-status-killed)"],
    ["SKIPPED", "var(--ks-status-skipped)"],
    ["CANCELLED", "var(--ks-status-cancelled)"],
    ["RETRIED", "var(--ks-status-retried)"],
    ["RESUBMITTED", "var(--ks-status-resubmitted)"],
    [RUNNING_STATE, "var(--ks-status-running)"],
    ["KILLING", "var(--ks-status-killing)"],
    ["PAUSED", "var(--ks-status-paused)"],
    ["BREAKPOINT", "var(--ks-status-breakpoint)"],
    ["RETRYING", "var(--ks-status-retrying)"],
    ["RESTARTED", "var(--ks-status-restarted)"],
    ["QUEUED", "var(--ks-status-queued)"],
    ["SUBMITTED", "var(--ks-status-submitted)"],
    ["CREATED", "var(--ks-status-created)"],
]

const STATE_RANK = new Map(ORDERED_STATE_COLORS.map(([state], rank) => [state, rank]))

const STATE_COLORS = new Map(ORDERED_STATE_COLORS)

const isPositive = (count: unknown): count is number => typeof count === "number" && Number.isFinite(count) && count > 0

export function stateBarSegments(counts: StateCounts | undefined, {total, running}: StateBarOptions = {}): StateBarSegment[] {
    const merged: StateCounts = {...counts}
    if (isPositive(running)) {
        merged[RUNNING_STATE] = (isPositive(merged[RUNNING_STATE]) ? merged[RUNNING_STATE] : 0) + running
    }

    const rankOf = (state: string) => STATE_RANK.get(state) ?? STATE_RANK.size
    const segments: StateBarSegment[] = Object.entries(merged)
        .filter(([, count]) => isPositive(count))
        .sort(([a], [b]) => rankOf(a) - rankOf(b))
        .map(([state, count]) => ({state, count, color: STATE_COLORS.get(state) ?? NEUTRAL_COLOR, notStarted: false}))

    const notStarted = isPositive(total) ? total - segments.reduce((sum, segment) => sum + segment.count, 0) : 0
    if (notStarted > 0) {
        segments.push({state: NOT_STARTED, count: notStarted, color: NOT_STARTED_COLOR, notStarted: true})
    }

    return segments
}
