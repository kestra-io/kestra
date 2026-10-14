import {STATES, LOG_LEVELS} from "@kestra-io/design-system"
import {cssVar} from "@kestra-io/design-system"

export const getSchemes = () => {
    const executions = {} as Record<string, string>
    const EXECUTION_STATES = Object.values(STATES) as any[]
    for (const state of EXECUTION_STATES) {
        executions[state.name] = cssVar(`--ks-status-${state.name.toLowerCase()}`) ?? "transparent"
    }

    const logs = {} as Record<string, string>
    for (const level of LOG_LEVELS) {
        logs[level] = cssVar(`--ks-log-${level.toLowerCase()}`) ?? "transparent"
    }

    return {
        executions,
        logs,
    }
}

export const getSchemeValue = (state: string, type: "executions" | "logs" = "executions"): string => {
    return (getSchemes() as any)[type][state] ?? "transparent"
}