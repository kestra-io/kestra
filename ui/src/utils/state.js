export default class State {
    static get CREATED() {
        return "CREATED";
    }

    static get RESTARTED() {
        return "RESTARTED";
    }

    static get SUCCESS() {
        return "SUCCESS";
    }

    static get RUNNING() {
        return "RUNNING";
    }

    static get KILLING() {
        return "KILLING";
    }

    static get KILLED() {
        return "KILLED";
    }

    static get FAILED() {
        return "FAILED";
    }

    static colorClass() {
        return {
            [State.CREATED]: "info",
            [State.RESTARTED]: "info",
            [State.SUCCESS]: "success ",
            [State.RUNNING]: "primary",
            [State.KILLING]: "warning",
            [State.KILLED]: "warning",
            [State.FAILED]: "danger"
        };
    }

    static color() {
        return {
            [State.CREATED]: "#75bcdd",
            [State.RESTARTED]: "#75bcdd",
            [State.SUCCESS]: "#43ac6a",
            [State.RUNNING]: "#1AA5DE",
            [State.KILLING]: "#FBD10B",
            [State.KILLED]: "#FBD10B",
            [State.FAILED]: "#F04124"
        };
    }

    static isRunning(current) {
        return [State.KILLING, State.CREATED, State.RUNNING].includes(current);
    }

    static icon() {
        return {
            [State.CREATED]: "pause-circle-outline",
            [State.SUCCESS]: 'check-circle-outline',
            [State.RUNNING]: 'play-circle-outline',
            [State.FAILED]: 'close-circle-outline',
            [State.KILLING]: 'close-circle-outline',
            [State.KILLED]: 'stop-circle-outline'
        };
    }
}
