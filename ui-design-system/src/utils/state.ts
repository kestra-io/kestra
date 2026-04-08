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
import {cssVar} from "./css.ts";

interface StateModel {
    name: string;
    color: string;
    colorClass: string;
    icon: any;
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

const mapValues = <T, U>(obj: Record<string, T>, fn: (val: T) => U): Record<string, U> =>{
    return Object.fromEntries(Object.entries(obj).map(([k, v]) => [k, fn(v)])) as Record<string, U>;
}

export class State {
    static get CREATED() {
        return STATES.CREATED.name;
    }

    static get RESTARTED() {
        return STATES.RESTARTED.name;
    }

    static get SUCCESS() {
        return STATES.SUCCESS.name;
    }

    static get RUNNING() {
        return STATES.RUNNING.name;
    }

    static get KILLING() {
        return STATES.KILLING.name;
    }

    static get KILLED() {
        return STATES.KILLED.name;
    }

    static get FAILED() {
        return STATES.FAILED.name;
    }

    static get WARNING() {
        return STATES.WARNING.name;
    }

    static get PAUSED() {
        return STATES.PAUSED.name;
    }

    static get CANCELLED() {
        return STATES.CANCELLED.name;
    }

    static get SKIPPED() {
        return STATES.SKIPPED.name;
    }

    static get QUEUED() {
        return STATES.QUEUED.name;
    }

    static get RETRYING() {
        return STATES.RETRYING.name;
    }

    static get RETRIED() {
        return STATES.RETRIED.name;
    }

    static get BREAKPOINT() {
        return STATES.BREAKPOINT.name;
    }

    static isRunning(state:string) {
        return STATES[state] && STATES[state].isRunning;
    }

    static isKillable(state:string) {
        return STATES[state] && STATES[state].isKillable;
    }

    static isPaused(state:string) {
        return STATES[state] && STATES[state] === STATES.PAUSED;
    }

    static isFailed(state:string) {
        return STATES[state] && STATES[state].isFailed;
    }

    static isQueued(state:string) {
        return STATES[state] && STATES[state] === STATES.QUEUED;
    }

    static allStates() {
        return mapValues(STATES, (state:StateModel) => {
            return {
                key: state.name,
                icon: state.icon,
                color: ""
            }
        });
    }

    static arrayAllStates() {
        return Object.values(STATES);
    }

    static colorClass() {
        return mapValues(STATES, (state) => state.colorClass);
    }

    static color() {
        return mapValues(STATES, (state) =>
            cssVar("--ks-" + state.colorClass),
        );
    }

    static getStateColor(state:string) {
        return cssVar("--ks-" + STATES[state].colorClass);
    }

    static icon() {
        return mapValues(STATES, (state) => state.icon);
    }

    static getTerminatedStates() {
        return Object.values(STATES).filter(state => !state.isRunning).map(state => state.name);
    }
}
