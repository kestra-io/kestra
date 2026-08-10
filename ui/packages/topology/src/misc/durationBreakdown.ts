import type {Moment} from "moment"
import {State} from "@kestra-io/design-system"

export interface DurationHistoryEntry {
    date: Moment | string | number;
    state: string;
}

export interface DurationBreakdown {
    /** Total elapsed time in milliseconds, always equal to `queued + running`. */
    total: number;
    /** Time spent outside of a RUNNING span: before the first RUNNING entry, and every gap
     *  between a RUNNING span ending and the next one starting (e.g. inter-attempt waiting). */
    queued: number;
    /** Time spent inside RUNNING spans. */
    running: number;
    /** Whether the task run has not yet reached a terminal state. */
    isRunning: boolean;
}

function toMillis(date: Moment | string | number): number {
    if (typeof date === "number") return date
    if (typeof date === "string") return new Date(date).getTime()
    return date.valueOf()
}

/**
 * Splits a task run's flat state-history transitions into queued vs. running time.
 * A RUNNING entry opens a "running span" that lasts until the next transition, whatever its
 * state; every other span (before the first RUNNING, or between a RUNNING span ending and the
 * next one starting) counts as queued. `queued + running` is always exactly `total`.
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
        return {total: 0, queued: 0, running: 0, isRunning: false}
    }

    const last = entries[entries.length - 1]
    const isRunning = Boolean(State.isRunning(last.state))

    let queued = 0
    let running = 0
    for (let i = 0; i < entries.length - 1; i++) {
        const span = entries[i + 1].date - entries[i].date
        if (entries[i].state === "RUNNING") {
            running += span
        } else {
            queued += span
        }
    }

    if (isRunning) {
        const tail = Math.max(0, now - last.date)
        if (last.state === "RUNNING") {
            running += tail
        } else {
            queued += tail
        }
    }

    return {total: queued + running, queued, running, isRunning}
}
