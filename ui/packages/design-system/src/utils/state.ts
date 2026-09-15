import type {Component} from "vue"
import PauseCircle from "vue-material-design-icons/PauseCircle.vue"
import CheckCircle from "vue-material-design-icons/CheckCircle.vue"
import PlayCircle from "vue-material-design-icons/PlayCircle.vue"
import CloseCircle from "vue-material-design-icons/CloseCircle.vue"
import StopCircle from "vue-material-design-icons/StopCircle.vue"
import SkipPreviousCircle from "vue-material-design-icons/SkipPreviousCircle.vue"
import AlertCircle from "vue-material-design-icons/AlertCircle.vue"
import DotsVerticalCircle from "vue-material-design-icons/DotsVerticalCircle.vue"
import MotionPauseOutline from "vue-material-design-icons/MotionPauseOutline.vue"
import Refresh from "vue-material-design-icons/Refresh.vue"
import Cancel from "vue-material-design-icons/Cancel.vue"
import {cssVar} from "./css"

interface StateModel {
    name: string;
    icon: Component;
    isRunning: boolean;
    isKillable: boolean;
    isFailed: boolean;
}

export const LOG_LEVELS = ["ERROR", "WARN", "INFO", "DEBUG", "TRACE"] as const

export const STATES:Record<string, StateModel> = Object.freeze({
    SUBMITTED: {
        name: "SUBMITTED",
        icon: DotsVerticalCircle,
        isRunning: true,
        isKillable: true,
        isFailed: false,
    },
    CREATED: {
        name: "CREATED",
        icon: DotsVerticalCircle,
        isRunning: true,
        isKillable: true,
        isFailed: false,
    },
    RESTARTED: {
        name: "RESTARTED",
        icon: SkipPreviousCircle,
        isRunning: false,
        isKillable: true,
        isFailed: false,
    },
    SUCCESS: {
        name: "SUCCESS",
        icon: CheckCircle,
        isRunning: false,
        isKillable: false,
        isFailed: false,
    },
    RUNNING: {
        name: "RUNNING",
        icon: PlayCircle,
        isRunning: true,
        isKillable: true,
        isFailed: false,
    },
    KILLING: {
        name: "KILLING",
        icon: CloseCircle,
        isRunning: true,
        isKillable: true,
        isFailed: true,
    },
    KILLED: {
        name: "KILLED",
        icon: StopCircle,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    WARNING: {
        name: "WARNING",
        icon: AlertCircle,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    FAILED: {
        name: "FAILED",
        icon: CloseCircle,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    PAUSED: {
        name: "PAUSED",
        icon: PauseCircle,
        isRunning: true,
        isKillable: true,
        isFailed: false,
    },
    CANCELLED: {
        name: "CANCELLED",
        icon: Cancel,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    SKIPPED: {
        name: "SKIPPED",
        icon: Cancel,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    QUEUED: {
        name: "QUEUED",
        icon: MotionPauseOutline,
        isRunning: false,
        isKillable: false,
        isFailed: false,
    },
    RETRYING: {
        name: "RETRYING",
        icon: Refresh,
        isRunning: false,
        isKillable: true,
        isFailed: false,
    },
    RETRIED: {
        name: "RETRIED",
        icon: Refresh,
        isRunning: false,
        isKillable: false,
        isFailed: false,
    },
    BREAKPOINT: {
        name: "BREAKPOINT",
        icon: PauseCircle,
        isRunning: true,
        isKillable: true,
        isFailed: false,
    },
})

const mapValues = <T, U>(obj: Record<string, T>, fn: (val: T) => U): Record<string, U> =>
    Object.fromEntries(Object.entries(obj).map(([k, v]) => [k, fn(v)]))

export const CREATED = "CREATED" as const
export const RESTARTED = "RESTARTED" as const
export const SUCCESS = "SUCCESS" as const
export const RUNNING = "RUNNING" as const
export const KILLING = "KILLING" as const
export const KILLED = "KILLED" as const
export const FAILED = "FAILED" as const
export const WARNING = "WARNING" as const
export const PAUSED = "PAUSED" as const
export const CANCELLED = "CANCELLED" as const
export const SKIPPED = "SKIPPED" as const
export const RETRIED = "RETRIED" as const
export const BREAKPOINT = "BREAKPOINT" as const

export function isRunning(state:string) {
    return STATES[state]?.isRunning
}

export function isKillable(state:string) {
    return STATES[state]?.isKillable
}

export function isPaused(state:string) {
    return STATES[state] === STATES.PAUSED
}

export function isFailed(state:string) {
    return STATES[state]?.isFailed
}

export function isQueued(state:string) {
    return STATES[state] === STATES.QUEUED
}

export function allStates() {
    return mapValues(STATES, (state:StateModel) => ({
        key: state.name,
        icon: state.icon,
        color: "",
    }))
}

export function arrayAllStates() {
    return Object.values(STATES)
}

export function color() {
    return mapValues(STATES, (state) => cssVar(`--ks-status-${state.name.toLowerCase()}`))
}

export function getStateColor(state:string) {
    return cssVar(`--ks-status-${STATES[state].name.toLowerCase()}`)
}

export function getTerminatedStates() {
    return Object.values(STATES).filter(state => !state.isRunning).map(state => state.name)
}
