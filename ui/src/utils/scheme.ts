import {computed} from "vue";
import {useTheme} from "./utils"
import {cssVariable} from "@kestra-io/ui-libs";

const executionStates = [
    "CANCELLED",
    "CREATED",
    "FAILED",
    "KILLED",
    "KILLING",
    "PAUSED",
    "QUEUED",
    "RESTARTED",
    "RETRIED",
    "RETRYING",
    "RUNNING",
    "SKIPPED",
    "SUCCESS",
    "WARNING"
] as const;

const logLevels = [
    "DEBUG",
    "ERROR",
    "INFO",
    "TRACE",
    "WARN"
]

function getSchemes() {
    const executions = {} as Record<typeof executionStates[number], string>
    for(const state of executionStates) {
        executions[state] = cssVariable(`--ks-chart-${state.toLowerCase()}`) ?? "transparent";
    }

    const logs = {} as Record<typeof logLevels[number], string>
    for(const level of logLevels) {
        logs[level] = cssVariable(`--ks-chart-${level.toLowerCase()}`) ?? "transparent";
    }

    return {
        executions,
        logs,
    }
}

export function getSchemeValue(state: typeof executionStates[number], type:"executions"): string;
export function getSchemeValue(state: typeof logLevels[number], type:"logs"): string;

export function getSchemeValue(state: string, type:"executions"|"logs" = "executions" ){
    return (getSchemes() as any)[type][state] ?? "transparent";
};

/**
 *
 * @param {"executions" | "logs"} type - what th chart needed will display
 * @returns
 */
export const useScheme = (type = "executions") => {
    const theme = useTheme();
    return computed(() => {
        const TYPES = getSchemes();
        // force recalculation of css variables on theme change
        if(theme.value !== undefined) {
            return TYPES[type as keyof typeof TYPES] ?? {};
        }else {
            return {}
        }
    });
}