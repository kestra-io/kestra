import _mapValues from "lodash/mapValues";
import PauseCircle from "vue-material-design-icons/PauseCircle";
import CheckCircle from "vue-material-design-icons/CheckCircle";
import PlayCircle from "vue-material-design-icons/PlayCircle";
import CloseCircle from "vue-material-design-icons/CloseCircle";
import StopCircle from "vue-material-design-icons/StopCircle";
import Restart from "vue-material-design-icons/Restart";
import AlertCircle from "vue-material-design-icons/AlertCircle";
import ProgressWrench from "vue-material-design-icons/ProgressWrench";

const STATE = Object.freeze({
    CREATED: {
        name: "CREATED",
        colorClass: "info",
        color: "#4F83A5",
        icon: ProgressWrench,
        isRunning: true,
        isKillable: true,
        isFailed: false,
    },
    RESTARTED: {
        name: "RESTARTED",
        colorClass: "info",
        color: "#4F83A5",
        icon: Restart,
        isRunning: false,
        isKillable: true,
        isFailed: false,
    },
    SUCCESS: {
        name: "SUCCESS",
        colorClass: "success",
        color: "#22b783",
        icon: CheckCircle,
        isRunning: false,
        isKillable: false,
        isFailed: false,
    },
    RUNNING: {
        name: "RUNNING",
        colorClass: "primary",
        color: "#4F83A5",
        icon: PlayCircle,
        isRunning: true,
        isKillable: true,
        isFailed: false,
    },
    KILLING: {
        name: "KILLING",
        colorClass: "warning",
        color: "#ffb822",
        icon: CloseCircle,
        isRunning: true,
        isKillable: false,
        isFailed: true,
    },
    KILLED: {
        name: "KILLED",
        colorClass: "warning",
        color: "#ffb822",
        icon: StopCircle,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    WARNING: {
        name: "WARNING",
        colorClass: "warning",
        color: "#ff8500",
        icon: AlertCircle,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    FAILED: {
        name: "FAILED",
        colorClass: "danger",
        color: "#f5325c",
        icon: CloseCircle,
        isRunning: false,
        isKillable: false,
        isFailed: true,
    },
    PAUSED: {
        name: "PAUSED",
        colorClass: "purple",
        color: "#6d81f5",
        icon: PauseCircle,
        isRunning: false,
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

    static type() {
        return _mapValues(STATE, state => state.colorClass);
    }

    static color() {
        return _mapValues(STATE, state => state.color);
    }

    static icon() {
        return _mapValues(STATE, state => state.icon);
    }
}
