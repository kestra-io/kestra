import _mapValues from "lodash/mapValues";
import _map from "lodash/map";

const STATE = Object.freeze({
    CREATED: {
        name: "CREATED",
        colorClass: "info",
        color: "#75bcdd",
        icon: "pause-circle-outline",
        isRunning: true,
        isKillable: true
    },
    RESTARTED: {
        name: "RESTARTED",
        colorClass: "info",
        color: "#75bcdd",
        icon: "restart",
        isRunning: false,
        isKillable: true
    },
    SUCCESS: {
        name: "SUCCESS",
        colorClass: "success",
        color: "#43ac6a",
        icon: "check-circle-outline",
        isRunning: false,
        isKillable: false
    },
    RUNNING: {
        name: "RUNNING",
        colorClass: "primary",
        color: "#1AA5DE",
        icon: "play-circle-outline",
        isRunning: true,
        isKillable: true
    },
    KILLING: {
        name: "KILLING",
        colorClass: "warning",
        color: "#FBD10B",
        icon: "close-circle-outline",
        isRunning: true,
        isKillable: false
    },
    KILLED: {
        name: "KILLED",
        colorClass: "warning",
        color: "#FBD10B",
        icon: "stop-circle-outline",
        isRunning: false,
        isKillable: false
    },
    WARNING: {
        name: "WARNING",
        colorClass: "warning",
        color: "#FBD10B",
        icon: "alert-circle",
        isRunning: false,
        isKillable: false
    },
    FAILED: {
        name: "FAILED",
        colorClass: "danger",
        color: "#F04124",
        icon: "close-circle-outline",
        isRunning: false,
        isKillable: false
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

    static isRunning(state) {
        return state[state] && state[state].isRunning;
    }

    static isKillable(state) {
        return state[state] && state[state].isKillable;
    }

    static allStates() {
        return _map(STATE, "name");
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
