import _mapValues from "lodash/mapValues";
import PauseCircle from "vue-material-design-icons/PauseCircle.vue";
import CheckCircle from "vue-material-design-icons/CheckCircle.vue";
import PlayCircle from "vue-material-design-icons/PlayCircle.vue";
import CloseCircle from "vue-material-design-icons/CloseCircle.vue";
import StopCircle from "vue-material-design-icons/StopCircle.vue";
import Restart from "vue-material-design-icons/Restart.vue";
import AlertCircle from "vue-material-design-icons/AlertCircle.vue";
import ProgressWrench from "vue-material-design-icons/ProgressWrench.vue";

const STATE = Object.freeze({
    CREATED: {
        name: "CREATED",
        colorClass: "info",
        color: "#9F9DFF",
        icon: ProgressWrench,
        isRunning: true,
        isKillable: true,
        isFailed: false,
    },
    RESTARTED: {
        name: "RESTARTED",
        colorClass: "info",
        color: "#9F9DFF",
        icon: Restart,
        isRunning: false,
        isKillable: true,
        isFailed: false,
    },
    SUCCESS: {
        name: "SUCCESS",
        colorClass: "success",
        color: "#03DABA",
        icon: CheckCircle,
        isRunning: false,
        isKillable: false,
        isFailed: false,
    },
    RUNNING: {
        name: "RUNNING",
        colorClass: "primary",
        color: "#9F9DFF",
        icon: PlayCircle,
        isRunning: true,
        isKillable: true,
        isFailed: false,
    },
    KILLING: {
        name: "KILLING",
        colorClass: "warning",
        color: "#FCE07C",
        icon: CloseCircle,
        isRunning: true,
        isKillable: false,
        isFailed: true,
    },
    KILLED: {
        name: "KILLED",
        colorClass: "warning",
        color: "#FCE07C",
        icon: StopCircle,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    WARNING: {
        name: "WARNING",
        colorClass: "warning",
        color: "#FCB37C",
        icon: AlertCircle,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    FAILED: {
        name: "FAILED",
        colorClass: "danger",
        color: "#F1444C",
        icon: CloseCircle,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    PAUSED: {
        name: "PAUSED",
        colorClass: "",
        color: "#8405FF",
        icon: PauseCircle,
        isRunning: true,
        isKillable: false,
        isFailed: false,
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

    static isRunning(state) {
        return STATE[state] && STATE[state].isRunning;
    }

    static isKillable(state) {
        return STATE[state] && STATE[state].isKillable;
    }

    static isFailed(state) {
        return STATE[state] && STATE[state].isFailed;
    }

    static allStates() {
        return _mapValues(STATE, state => {
            return {
                key: state.name,
                icon: state.icon,
                color: state.color
            }
        });
    }

    static colorClass() {
        return _mapValues(STATE, state => state.colorClass);
    }

    static color() {
        return _mapValues(STATE, state => state.color);
    }

    static icon() {
        return _mapValues(STATE, state => state.icon);
    }
}
