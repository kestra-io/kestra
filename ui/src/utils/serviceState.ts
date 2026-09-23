import type {ServiceServiceState} from "@kestra-io/kestra-sdk"
import type {ExecutionStatus} from "@kestra-io/design-system"

export const SERVICE_STATE_TO_EXECUTION_STATUS: Record<ServiceServiceState, ExecutionStatus> = {
    RUNNING: "SUCCESS",
    ERROR: "FAILED",
    DISCONNECTED: "FAILED",
    INACTIVE: "PAUSED",
    CREATED: "PAUSED",
    MAINTENANCE: "WARNING",
    NOT_RUNNING: "PAUSED",
    TERMINATED_FORCED: "FAILED",
    TERMINATED_GRACEFULLY: "PAUSED",
    TERMINATING: "WARNING",
}
