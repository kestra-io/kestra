import {computed} from "vue";
import {useLocalStorage} from "@vueuse/core";
import {useTheme} from "./utils"
import {cssVariable} from "@kestra-io/ui-libs";

const SCHEME = "scheme";
const EXECUTIONS = Object.freeze({
    CANCELLED: cssVariable("--ks-chart-cancelled"),
    CREATED: cssVariable("--ks-chart-created"),
    FAILED: cssVariable("--ks-chart-failed"),
    KILLED: cssVariable("--ks-chart-killed"),
    KILLING: cssVariable("--ks-chart-killing"),
    PAUSED: cssVariable("--ks-chart-paused"),
    QUEUED: cssVariable("--ks-chart-queued"),
    RESTARTED: cssVariable("--ks-chart-restarted"),
    RETRIED: cssVariable("--ks-chart-retried"),
    RETRYING: cssVariable("--ks-chart-retrying"),
    RUNNING: cssVariable("--ks-chart-running"),
    SKIPPED: cssVariable("--ks-chart-skipped"),
    SUCCESS: cssVariable("--ks-chart-success"),
    WARNING: cssVariable("--ks-chart-warning"),
});
const LOGS = Object.freeze({
    DEBUG: cssVariable("--ks-chart-debug"),
    ERROR: cssVariable("--ks-chart-error"),
    INFO: cssVariable("--ks-chart-info"),
    TRACE: cssVariable("--ks-chart-trace"),
    WARN: cssVariable("--ks-chart-warn"),
});
const TYPES = Object.freeze({
    executions: EXECUTIONS,
    logs: LOGS,
});
/**
 * Allows scheme/theme customization.
 */
const OPTIONS = Object.freeze({
    classic: {
        light: TYPES,
        dark: TYPES,
    },
    kestra: {
        light: TYPES,
        dark: TYPES,
    },
});

export const setScheme = (value) => {
    localStorage.setItem(SCHEME, value);
};

export const getScheme = (theme, state, type = "executions") => {
    const scheme = localStorage.getItem(SCHEME) ?? "classic";

    return OPTIONS[scheme]?.[theme]?.[type]?.[state];
};

export const useScheme = (type = "executions") => {
    const scheme = useLocalStorage(SCHEME, "classic");
    const theme = useTheme();

    return computed(() => OPTIONS[scheme.value]?.[theme.value]?.[type]);
}