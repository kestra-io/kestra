import type {Component} from "vue";
import PauseCircle from "vue-material-design-icons/PauseCircle.vue";
import CheckCircle from "vue-material-design-icons/CheckCircle.vue";
import PlayCircle from "vue-material-design-icons/PlayCircle.vue";
import CloseCircle from "vue-material-design-icons/CloseCircle.vue";
import StopCircle from "vue-material-design-icons/StopCircle.vue";
import SkipPreviousCircle from "vue-material-design-icons/SkipPreviousCircle.vue";
import AlertCircle from "vue-material-design-icons/AlertCircle.vue";
import DotsVerticalCircle from "vue-material-design-icons/DotsVerticalCircle.vue";
import MotionPauseOutline from "vue-material-design-icons/MotionPauseOutline.vue";
import Refresh from "vue-material-design-icons/Refresh.vue";
import Cancel from "vue-material-design-icons/Cancel.vue";
import {cssVar} from "./css";

interface StateModel {
    name: string;
    color: string;
    colorClass: string;
    icon: Component;
    isRunning: boolean;
    isKillable: boolean;
    isFailed: boolean;
}

export const LOG_LEVELS = ["ERROR", "WARN", "INFO", "DEBUG", "TRACE"] as const;

export const STATES:Record<string, StateModel> = Object.freeze({
    CREATED: {
        name: "CREATED",
        color: "#1761FD",
        colorClass: "blue-500",
        icon: DotsVerticalCircle,
        isRunning: true,
        isKillable: true,
        isFailed: false,
    },
    RESTARTED: {
        name: "RESTARTED",
        color: "#1761FD",
        colorClass: "blue-500",
        icon: SkipPreviousCircle,
        isRunning: false,
        isKillable: true,
        isFailed: false,
    },
    SUCCESS: {
        name: "SUCCESS",
        color: "#029E73",
        colorClass: "green-500",
        icon: CheckCircle,
        isRunning: false,
        isKillable: false,
        isFailed: false,
    },
    RUNNING: {
        name: "RUNNING",
        color: "#8405FF",
        colorClass: "purple-500",
        icon: PlayCircle,
        isRunning: true,
        isKillable: true,
        isFailed: false,
    },
    KILLING: {
        name: "KILLING",
        color: "#FCE07C",
        colorClass: "yellow-200",
        icon: CloseCircle,
        isRunning: true,
        isKillable: true,
        isFailed: true,
    },
    KILLED: {
        name: "KILLED",
        color: "#FCE07C",
        colorClass: "yellow-500",
        icon: StopCircle,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    WARNING: {
        name: "WARNING",
        color: "#DD5F00",
        colorClass: "orange-500",
        icon: AlertCircle,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    FAILED: {
        name: "FAILED",
        color: "#AB0009",
        colorClass: "red-500",
        icon: CloseCircle,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    PAUSED: {
        name: "PAUSED",
        color: "#918BA9",
        colorClass: "purple-200",
        icon: PauseCircle,
        isRunning: true,
        isKillable: true,
        isFailed: false,
    },
    CANCELLED: {
        name: "CANCELLED",
        color: "#918BA9",
        colorClass: "gray-300",
        icon: Cancel,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    SKIPPED: {
        name: "SKIPPED",
        color: "#918BA9",
        colorClass: "gray-300",
        icon: Cancel,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    QUEUED: {
        name: "QUEUED",
        color: "#918BA9",
        colorClass: "gray",
        icon: MotionPauseOutline,
        isRunning: false,
        isKillable: false,
        isFailed: false,
    },
    RETRYING: {
        name: "RETRYING",
        color: "#918BA9",
        colorClass: "gray-300",
        icon: Refresh,
        isRunning: false,
        isKillable: true,
        isFailed: false,
    },
    RETRIED: {
        name: "RETRIED",
        color: "#918BA9",
        colorClass: "gray-300",
        icon: Refresh,
        isRunning: false,
        isKillable: false,
        isFailed: false,
    },
    BREAKPOINT: {
        name: "BREAKPOINT",
        color: "#918BA9",
        colorClass: "gray-300",
        icon: PauseCircle,
        isRunning: false,
        isKillable: false,
        isFailed: false,
    },
});

const mapValues = <T, U>(obj: Record<string, T>, fn: (val: T) => U): Record<string, U> =>
    Object.fromEntries(Object.entries(obj).map(([k, v]) => [k, fn(v)]));

export class State {
    static readonly CREATED = "CREATED" as const;
    static readonly RESTARTED = "RESTARTED" as const;
    static readonly SUCCESS = "SUCCESS" as const;
    static readonly RUNNING = "RUNNING" as const;
    static readonly KILLING = "KILLING" as const;
    static readonly KILLED = "KILLED" as const;
    static readonly FAILED = "FAILED" as const;
    static readonly WARNING = "WARNING" as const;
    static readonly PAUSED = "PAUSED" as const;
    static readonly CANCELLED = "CANCELLED" as const;
    static readonly SKIPPED = "SKIPPED" as const;
    static readonly QUEUED = "QUEUED" as const;
    static readonly RETRYING = "RETRYING" as const;
    static readonly RETRIED = "RETRIED" as const;
    static readonly BREAKPOINT = "BREAKPOINT" as const;

    static isRunning(state:string) {
        return STATES[state]?.isRunning;
    }

    static isKillable(state:string) {
        return STATES[state]?.isKillable;
    }

    static isPaused(state:string) {
        return STATES[state] === STATES.PAUSED;
    }

    static isFailed(state:string) {
        return STATES[state]?.isFailed;
    }

    static isQueued(state:string) {
        return STATES[state] === STATES.QUEUED;
    }

    static allStates() {
        return mapValues(STATES, (state:StateModel) => ({
            key: state.name,
            icon: state.icon,
            color: "",
        }));
    }

    static arrayAllStates() {
        return Object.values(STATES);
    }

    static colorClass() {
        return mapValues(STATES, (state) => state.colorClass);
    }

    static color() {
        return mapValues(STATES, (state) => cssVar(`--ks-chart-${state.name.toLowerCase()}`));
    }

    static getStateColor(state:string) {
        return cssVar(`--ks-chart-${STATES[state].name.toLowerCase()}`);
    }

    static icon() {
        return mapValues(STATES, (state) => state.icon);
    }

    static getTerminatedStates() {
        return Object.values(STATES).filter(state => !state.isRunning).map(state => state.name);
    }
}
