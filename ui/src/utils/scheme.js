const SCHEME = "scheme";
const OPTIONS = Object.freeze({
    default: {
        light: {
            executions: {
                CANCELLED: "#fd9297",
                CREATED: "#3991ff",
                FAILED: "#fd7278",
                KILLED: "#bda85d",
                KILLING: "#fde89d",
                PAUSED: "#a6a4ca",
                QUEUED: "#7e719f",
                RESTARTED: "#c7f0ff",
                RETRIED: "#a2cdff",
                RETRYING: "#7fbbff",
                RUNNING: "#5bb8ff",
                SUCCESS: "#21ce9c",
                WARNING: "#eeae7e",
            },
            logs: {
                DEBUG: "#3991ff",
                ERROR: "#fd7278",
                INFO: "#21ce9c",
                TRACE: "#9a8eb4",
                WARN: "#f3c4a1",
            },
        },
        dark: {
            executions: {
                CANCELLED: "#fd9297",
                CREATED: "#3991ff",
                FAILED: "#fd7278",
                KILLED: "#bda85d",
                KILLING: "#fde89d",
                PAUSED: "#a6a4ca",
                QUEUED: "#7e719f",
                RESTARTED: "#c7f0ff",
                RETRIED: "#a2cdff",
                RETRYING: "#7fbbff",
                RUNNING: "#5bb8ff",
                SUCCESS: "#21ce9c",
                WARNING: "#eeae7e",
            },
            logs: {
                DEBUG: "#3991ff",
                ERROR: "#fd7278",
                INFO: "#21ce9c",
                TRACE: "#9a8eb4",
                WARN: "#f3c4a1",
            },
        },
    },
    purple: {
        light: {
            executions: {
                CANCELLED: "#fec9cb",
                CREATED: "#3991ff",
                FAILED: "#ff4bbd",
                KILLED: "#bda85d",
                KILLING: "#fde89d",
                PAUSED: "#a6a4ca",
                QUEUED: "#9a8eb4",
                RESTARTED: "#c7f0ff",
                RETRIED: "#a2cdff",
                RETRYING: "#7fbbff",
                RUNNING: "#5bb8ff",
                SUCCESS: "#8c4bff",
                WARNING: "#cf5df9",
            },
            logs: {
                DEBUG: "#3991ff",
                ERROR: "#ff4bbd",
                INFO: "#8c4bff",
                TRACE: "#a6a4ca",
                WARN: "#cf5df9",
            },
        },
        dark: {
            executions: {
                CANCELLED: "#fec9f5",
                CREATED: "#3991ff",
                FAILED: "#ff41b9",
                KILLED: "#fdedb3",
                KILLING: "#fce07c",
                PAUSED: "#d1cfe9",
                QUEUED: "#e5e4f7",
                RESTARTED: "#c7f0ff",
                RETRIED: "#a2cdff",
                RETRYING: "#7fbbff",
                RUNNING: "#5bb8ff",
                SUCCESS: "#9470ff",
                WARNING: "#cc4af8",
            },
            logs: {
                DEBUG: "#7fbbff",
                ERROR: "#ff41b9",
                INFO: "#9470ff",
                TRACE: "#d1cfe9",
                WARN: "#cc4af8",
            },
        },
    },
});

export const setScheme = (value) => {
    localStorage.setItem(SCHEME, value);
};

export const getScheme = (state, type = "executions") => {
    const scheme = localStorage.getItem(SCHEME) ?? "default";
    const theme = localStorage.getItem("theme") ?? "light";

    return OPTIONS[scheme][theme][type][state];
};
