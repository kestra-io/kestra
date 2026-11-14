import {State} from "@kestra-io/design-system"
import action from "../../models/action"
import type {Execution} from "../../stores/executions"

const PAUSABLE_STATES = [State.RUNNING, State.PAUSING, State.KILLING]

export const isExecutionTerminated = (execution: Execution): boolean => {
    return State.isTerminated(execution.state?.current ?? "")
}

export interface BulkActionRule {
    allowed: (execution: Execution) => boolean;
    tooltipKey: string;
    tooltipParams?: Record<string, string>;
}

export const bulkActionRules: Record<string, BulkActionRule> = {
    [action.UPDATE]: {
        allowed: (execution: Execution) => isExecutionTerminated(execution) && execution.state?.current !== State.KILLED,
        tooltipKey: "bulk action ineligible change state",
    },
    [action.RESTART]: {
        allowed: (execution: Execution) => execution.state?.current === State.FAILED,
        tooltipKey: "bulk action ineligible not state",
        tooltipParams: {state: State.FAILED},
    },
    [action.REPLAY]: {
        allowed: isExecutionTerminated,
        tooltipKey: "bulk action ineligible not terminated",
    },
    [action.KILL]: {
        allowed: (execution: Execution) => !isExecutionTerminated(execution),
        tooltipKey: "bulk action ineligible already terminated",
    },
    [action.PAUSE]: {
        allowed: (execution: Execution) => PAUSABLE_STATES.includes(execution.state?.current ?? ""),
        tooltipKey: "bulk action ineligible not pausable",
    },
    [action.RESUME]: {
        allowed: (execution: Execution) => execution.state?.current === State.PAUSED,
        tooltipKey: "bulk action ineligible not state",
        tooltipParams: {state: State.PAUSED},
    },
    [action.UNQUEUE]: {
        allowed: (execution: Execution) => execution.state?.current === State.QUEUED,
        tooltipKey: "bulk action ineligible not state",
        tooltipParams: {state: State.QUEUED},
    },
    [action.FORCE_RUN]: {
        allowed: (execution: Execution) => !isExecutionTerminated(execution),
        tooltipKey: "bulk action ineligible already terminated",
    },
    [action.CHANGE_LABELS]: {
        allowed: isExecutionTerminated,
        tooltipKey: "bulk action ineligible not terminated",
    },
}


