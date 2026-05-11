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

export interface ExecutionStatusModel {
    name: string;
    icon: Component;
    isRunning: boolean;
    isKillable: boolean;
    isFailed: boolean;
}

export const EXECUTION_STATUSES: Record<string, ExecutionStatusModel> = Object.freeze({
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
        isRunning: false,
        isKillable: false,
        isFailed: false,
    },
})

export type ExecutionStatus = keyof typeof EXECUTION_STATUSES;
