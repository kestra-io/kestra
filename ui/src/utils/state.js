import _mapValues from "lodash/mapValues";
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
import {cssVariable} from "@kestra-io/ui-libs/src/utils/global";

const STATE = Object.freeze({
    CREATED: {
        name: "CREATED",
        colorClass: "cyan",
        icon: DotsVerticalCircle,
        isRunning: true,
        isKillable: true,
        isFailed: false,
    },
    RESTARTED: {
        name: "RESTARTED",
        colorClass: "cyan",
        icon: SkipPreviousCircle,
        isRunning: false,
        isKillable: true,
        isFailed: false,
    },
    SUCCESS: {
        name: "SUCCESS",
        colorClass: "green",
        icon: CheckCircle,
        isRunning: false,
        isKillable: false,
        isFailed: false,
    },
    RUNNING: {
        name: "RUNNING",
        colorClass: "purple",
        icon: PlayCircle,
        isRunning: true,
        isKillable: true,
        isFailed: false,
    },
    KILLING: {
        name: "KILLING",
        colorClass: "yellow",
        icon: CloseCircle,
        isRunning: true,
        isKillable: true,
        isFailed: true,
    },
    KILLED: {
        name: "KILLED",
        colorClass: "yellow",
        icon: StopCircle,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    WARNING: {
        name: "WARNING",
        colorClass: "orange",
        icon: AlertCircle,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    FAILED: {
        name: "FAILED",
        colorClass: "red",
        icon: CloseCircle,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    PAUSED: {
        name: "PAUSED",
        colorClass: "indigo",
        icon: PauseCircle,
        isRunning: true,
        isKillable: true,
        isFailed: false,
    },
    CANCELLED: {
        name: "CANCELLED",
        colorClass: "gray",
        icon: Cancel,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    QUEUED: {
        name: "QUEUED",
        colorClass: "gray",
        icon: MotionPauseOutline,
        isRunning: false,
        isKillable: false,
        isFailed: false,
    },
    RETRYING: {
        name: "RETRYING",
        colorClass: "gray",
        icon: Refresh,
        isRunning: false,
        isKillable: true,
        isFailed: false
    },
    RETRIED: {
        name: "RETRIED",
        colorClass: "gray",
        icon: Refresh,
        isRunning: false,
        isKillable: false,
        isFailed: false
    }
});

export default class State {
    static get CREATED() {
        return STATE.CREATED.name;
    }

    static get RESTARTED() {
        return STATE.RESTARTED.name;
    }

    static get SUCCESS() {
        return STATE.SUCCESS.name;
    }

    static get RUNNING() {
        return STATE.RUNNING.name;
    }

    static get KILLING() {
        return STATE.KILLING.name;
    }

    static get KILLED() {
        return STATE.KILLED.name;
    }

    static get FAILED() {
        return STATE.FAILED.name;
    }

    static get WARNING() {
        return STATE.WARNING.name;
    }

    static get PAUSED() {
        return STATE.PAUSED.name;
    }

    static get CANCELLED() {
        return STATE.CANCELLED.name;
    }

    static get QUEUED() {
        return STATE.QUEUED.name;
    }

    static get RETRYING() {
        return STATE.RETRYING.name;
    }

    static get RETRIED() {
        return STATE.RETRIED.name;
    }

    static isRunning(state) {
        return STATE[state] && STATE[state].isRunning;
    }

    static isKillable(state) {
        return STATE[state] && STATE[state].isKillable;
    }

    static isPaused(state) {
        return STATE[state] && STATE[state] === STATE.PAUSED;
    }

    static isFailed(state) {
        return STATE[state] && STATE[state].isFailed;
    }

    static allStates() {
        return _mapValues(STATE, state => {
            return {
                key: state.name,
                icon: state.icon,
                color: cssVariable("--bs-" + state.colorClass),
                colorClass: state.colorClass
            }
        });
    }

    static arrayAllStates() {
        return Object.values(STATE);
    }

    static colorClass() {
        return _mapValues(STATE, state => state.colorClass);
    }

    static color() {
        return _mapValues(STATE, state => cssVariable("--bs-" + state.colorClass));
    }

    static icon() {
        return _mapValues(STATE, state => state.icon);
    }
}
