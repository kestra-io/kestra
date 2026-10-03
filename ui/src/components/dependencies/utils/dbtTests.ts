import type {Component} from "vue"
import TestTube from "vue-material-design-icons/TestTube.vue"
import TestTubeOff from "vue-material-design-icons/TestTubeOff.vue"

const STATES = ["pass", "warn", "fail"] as const

export type DbtTestState = typeof STATES[number]

export type DbtTests = {
    state: DbtTestState;
    total: number;
    failed: number;
};

const count = (value: unknown): number | undefined => (typeof value === "number" ? value : undefined)

/**
 * The producer writes all three keys together or none at all, so anything less is "no tests" and
 * renders nothing rather than `0/0`. An unrecognised status is treated the same way: reporting a
 * pass we cannot vouch for is the one failure this footer exists to prevent.
 */
export const normalizeDbtTests = (status: unknown, total: unknown, failed: unknown): DbtTests | undefined => {
    const totalCount = count(total)
    const state = STATES.find((known) => known === status)
    if (!totalCount || !state) {
        return undefined
    }

    return {state, total: totalCount, failed: count(failed) ?? 0}
}

export const dbtTestIconOf = (state: DbtTestState): Component => (state === "fail" ? TestTubeOff : TestTube)

/** Colour is reserved for bad news: a passing footer inherits the muted body colour instead. */
const STATE_TOKENS: Record<DbtTestState, string | undefined> = {
    pass: undefined,
    warn: "--ks-status-warning",
    fail: "--ks-status-error",
}

export const dbtTestColorOf = (state: DbtTestState): string | undefined => {
    const token = STATE_TOKENS[state]
    return token ? `var(${token})` : undefined
}
