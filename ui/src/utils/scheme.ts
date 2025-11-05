import {computed} from "vue";
import {useTheme} from "./utils"
import {cssVariable, STATES, LOG_LEVELS} from "@kestra-io/ui-libs";

export const getSchemes = () => {
    const executions = {} as Record<string, string>
    const EXECUTION_STATES = Object.values(STATES) as any[];
    for (const state of EXECUTION_STATES) {
        executions[state.name] = cssVariable(`--ks-chart-${state.name.toLowerCase()}`) ?? "transparent";
    }

    const logs = {} as Record<string, string>
    for (const level of LOG_LEVELS) {
        logs[level] = cssVariable(`--ks-chart-${level.toLowerCase()}`) ?? "transparent";
    }

    return {
        executions,
        logs,
    }
}

export const getSchemeValue = (state: string, type: "executions" | "logs" = "executions"): string => {
    return (getSchemes() as any)[type][state] ?? "transparent";
}

/**
 * @param {"executions" | "logs"} type - what the chart will display
 * @returns Computed scheme colors for the specified type
 */
export const useScheme = (type: "executions" | "logs" = "executions") => {
    const theme = useTheme();
    return computed(() => {
        const TYPES = getSchemes();
        if (theme.value !== undefined) {
            return TYPES[type as keyof typeof TYPES] ?? {};
        } else {
            return {}
        }
    });
}