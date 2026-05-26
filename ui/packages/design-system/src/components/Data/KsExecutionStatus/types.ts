import type {Component} from "vue"
import Pause from "vue-material-design-icons/Pause.vue"
import PauseOctagon from "vue-material-design-icons/PauseOctagon.vue"
import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
import PlayCircleOutline from "vue-material-design-icons/PlayCircleOutline.vue"
import CloseCircleOutline from "vue-material-design-icons/CloseCircleOutline.vue"
import StopCircle from "vue-material-design-icons/StopCircle.vue"
import StopCircleOutline from "vue-material-design-icons/StopCircleOutline.vue"
import BackupRestore from "vue-material-design-icons/BackupRestore.vue"
import AlertOutline from "vue-material-design-icons/AlertOutline.vue"
import PlusCircleOutline from "vue-material-design-icons/PlusCircleOutline.vue"
import Restore from "vue-material-design-icons/Restore.vue"
import Cancel from "vue-material-design-icons/Cancel.vue"
import SkipNextCircleOutline from "vue-material-design-icons/SkipNextCircleOutline.vue"
import ClockTimeFourOutline from "vue-material-design-icons/ClockTimeFourOutline.vue"
import Restart from "vue-material-design-icons/Restart.vue"

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
        icon: PlusCircleOutline,
        isRunning: true,
        isKillable: true,
        isFailed: false,
    },
    RESTARTED: {
        name: "RESTARTED",
        icon: BackupRestore,
        isRunning: false,
        isKillable: true,
        isFailed: false,
    },
    SUCCESS: {
        name: "SUCCESS",
        icon: CheckCircleOutline,
        isRunning: false,
        isKillable: false,
        isFailed: false,
    },
    RUNNING: {
        name: "RUNNING",
        icon: PlayCircleOutline,
        isRunning: true,
        isKillable: true,
        isFailed: false,
    },
    KILLING: {
        name: "KILLING",
        icon: StopCircleOutline,
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
        icon: AlertOutline,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    FAILED: {
        name: "FAILED",
        icon: CloseCircleOutline,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    PAUSED: {
        name: "PAUSED",
        icon: Pause,
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
        icon: SkipNextCircleOutline,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    QUEUED: {
        name: "QUEUED",
        icon: ClockTimeFourOutline,
        isRunning: false,
        isKillable: false,
        isFailed: false,
    },
    RETRYING: {
        name: "RETRYING",
        icon: Restart,
        isRunning: false,
        isKillable: true,
        isFailed: false,
    },
    RETRIED: {
        name: "RETRIED",
        icon: Restore,
        isRunning: false,
        isKillable: false,
        isFailed: false,
    },
    BREAKPOINT: {
        name: "BREAKPOINT",
        icon: PauseOctagon,
        isRunning: false,
        isKillable: false,
        isFailed: false,
    },
})

export type ExecutionStatus = keyof typeof EXECUTION_STATUSES;
