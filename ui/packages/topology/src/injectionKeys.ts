import type {ComputedRef, InjectionKey, Ref} from "vue"
import type {GraphExecution} from "./utils/vueFlowUtils"

export const EXECUTION_INJECTION_KEY = Symbol("execution-injection-key") as InjectionKey<ComputedRef<any>>
export const SUBFLOWS_EXECUTIONS_INJECTION_KEY = Symbol("subflows-executions-injection-key") as InjectionKey<ComputedRef<Record<string, GraphExecution>>>
export const SHOW_EXTRA_DETAILS_INJECTION_KEY = Symbol("show-extra-details-injection-key") as InjectionKey<Ref<boolean>>
export const VALIDATION_ISSUES_INJECTION_KEY = Symbol("validation-issues-injection-key") as InjectionKey<ComputedRef<Map<string, string[]>>>
export const FOCUSED_TASK_INJECTION_KEY = Symbol("focused-task-injection-key") as InjectionKey<ComputedRef<string | undefined>>
export const DROP_EDGE_INJECTION_KEY = Symbol("drop-edge-injection-key") as InjectionKey<ComputedRef<string | undefined>>
export const DRAGGING_NODE_INJECTION_KEY = Symbol("dragging-node-injection-key") as InjectionKey<ComputedRef<boolean>>
