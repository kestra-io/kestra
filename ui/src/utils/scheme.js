const SCHEME = "scheme";
const OPTIONS = Object.freeze({
    classic: {
        light: {
            executions: {
                CANCELLED: "#d1cfe9",
                CREATED: "#fec9cb",
                FAILED: "#fd7278",
                KILLED: "#e5e4f7",
                KILLING: "#d1cfe9",
                PAUSED: "#fce07c",
                QUEUED: "#fdedb3",
                RESTARTED: "#c7f0ff",
                RETRIED: "#a2cdff",
                RETRYING: "#7fbbff",
                RUNNING: "#5bb8ff",
                SUCCESS: "#02be8a",
                WARNING: "#e9985b",
            },
            logs: {
                DEBUG: "#7fbbff",
                ERROR: "#fd7278",
                INFO: "#21ce9c",
                TRACE: "#d1cfe9",
                WARN: "#eeae7e",
            },
        },
        dark: {
            executions: {
                CANCELLED: "#9a8eb4",
                CREATED: "#fd9297",
                FAILED: "#fd7278",
                KILLED: "#7e719f",
                KILLING: "#a6a4ca",
                PAUSED: "#fde89d",
                QUEUED: "#bda85d",
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
    kestra: {
        light: {
            executions: {
                CANCELLED: "#d1cfe9",
                CREATED: "#fec9f5",
                FAILED: "#ff41b9",
                KILLED: "#e5e4f7",
                KILLING: "#d1cfe9",
                PAUSED: "#fce07c",
                QUEUED: "#fdedb3",
                RESTARTED: "#c7f0ff",
                RETRIED: "#a2cdff",
                RETRYING: "#7fbbff",
                RUNNING: "#5bb8ff",
                SUCCESS: "#9470ff",
                WARNING: "#e9985b",
            },
            logs: {
                DEBUG: "#7fbbff",
                ERROR: "#ff41b9",
                INFO: "#9470ff",
                TRACE: "#d1cfe9",
                WARN: "#eeae7e",
            },
        },
        dark: {
            executions: {
                CANCELLED: "#a6a4ca",
                CREATED: "#fec9cb",
                FAILED: "#ff4bbd",
                KILLED: "#9a8eb4",
                KILLING: "#a6a4ca",
                PAUSED: "#fde89d",
                QUEUED: "#bda85d",
                RESTARTED: "#c7f0ff",
                RETRIED: "#a2cdff",
                RETRYING: "#7fbbff",
                RUNNING: "#5bb8ff",
                SUCCESS: "#8c4bff",
                WARNING: "#eeae7e",
            },
            logs: {
                DEBUG: "#3991ff",
                ERROR: "#ff4bbd",
                INFO: "#8c4bff",
                TRACE: "#a6a4ca",
                WARN: "#f3c4a1",
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
