import type {Execution} from "../../stores/executions"

export type ExecutionTaskRun = NonNullable<Execution["taskRunList"]>[number] & {outputs?: Record<string, unknown>}
