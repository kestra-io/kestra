import _mapValues from "lodash/mapValues";
import _map from "lodash/map";

export default class State {
    static States = Object.freeze({
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
        FAILED: {
            name: "FAILED",
            colorClass: "danger",
            color: "#F04124",
            icon: "close-circle-outline",
            isRunning: false,
            isKillable: false
        }
    });

    static get CREATED() {
        return this.States.CREATED.name;
    }

    static get RESTARTED() {
        return this.States.RESTARTED.name;
    }

    static get SUCCESS() {
        return this.States.SUCCESS.name;
    }

    static get RUNNING() {
        return this.States.RUNNING.name;
    }

    static get KILLING() {
        return this.States.KILLING.name;
    }

    static get KILLED() {
        return this.States.KILLED.name;
    }

    static get FAILED() {
        return this.States.FAILED.name;
    }

    static isRunning(state) {
        return this.States[state] && this.States[state].isRunning;
    }

    static isKillable(state) {
        return this.States[state] && this.States[state].isKillable;
    }

    static allStates() {
        return _map(this.States, "name");
    }

    static colorClass() {
        return _mapValues(this.States, state => state.colorClass);
    }

    static color() {
        return _mapValues(this.States, state => state.color);
    }

    static icon() {
        return _mapValues(this.States, state => state.icon);
    }
}
