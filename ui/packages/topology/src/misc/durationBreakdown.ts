import type {Moment} from "moment"
import {State} from "@kestra-io/design-system"

export interface DurationHistoryEntry {
    date: Moment | string | number;
    state: string;
}

export interface DurationBreakdown {
    /** Total elapsed time in milliseconds, always equal to `queued + running + paused`. */
    total: number;
    /** Time spent waiting to be picked up by a worker, including inter-attempt waiting. */
    queued: number;
    /** Time spent executing, including the teardown window while a run is being killed. */
    running: number;
    /** Time spent waiting on a human, from a paused run or a debug breakpoint. */
    paused: number;
    /** Whether the task run has not yet reached a terminal state. */
    isRunning: boolean;
}

type Bucket = "queued" | "running" | "paused"

const EXECUTING_STATES: string[] = [State.RUNNING, State.KILLING]
const WAITING_ON_HUMAN_STATES: string[] = [State.PAUSED, State.BREAKPOINT]

function bucketOf(state: string): Bucket {
    if (EXECUTING_STATES.includes(state)) return "running"
    if (WAITING_ON_HUMAN_STATES.includes(state)) return "paused"
    return "queued"
}

function toMillis(date: Moment | string | number): number {
    if (typeof date === "number") return date
    if (typeof date === "string") return new Date(date).getTime()
    return date.valueOf()
}

/**
 * Splits a task run's flat state-history transitions into queued, running and paused time.
 * Each transition opens a span that lasts until the next one and is attributed by the state that
 * opened it, so `queued + running + paused` is always exactly `total`.
 */
export function computeDurationBreakdown(
    histories: DurationHistoryEntry[],
    now: number = Date.now(),
): DurationBreakdown {
    const entries = histories
        .map((history) => ({date: toMillis(history.date), state: history.state}))
        .filter((history) => Number.isFinite(history.date) && Boolean(history.state))
        .sort((a, b) => a.date - b.date)

    if (entries.length === 0) {
        return {total: 0, queued: 0, running: 0, paused: 0, isRunning: false}
    }

    const last = entries[entries.length - 1]
    const isRunning = Boolean(State.isRunning(last.state))

    const spans: Record<Bucket, number> = {queued: 0, running: 0, paused: 0}

    for (let i = 0; i < entries.length - 1; i++) {
        spans[bucketOf(entries[i].state)] += entries[i + 1].date - entries[i].date
    }

    if (isRunning) {
        spans[bucketOf(last.state)] += Math.max(0, now - last.date)
    }

    return {
        total: spans.queued + spans.running + spans.paused,
        queued: spans.queued,
        running: spans.running,
        paused: spans.paused,
        isRunning,
    }
}
