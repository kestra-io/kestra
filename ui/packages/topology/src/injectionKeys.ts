import type {ComputedRef, InjectionKey} from "vue"
import type {GraphExecution} from "./utils/vueFlowUtils"
import type {LodLevel} from "./utils/constants"

export const EXECUTION_INJECTION_KEY = Symbol("execution-injection-key") as InjectionKey<ComputedRef<any>>
export const SUBFLOWS_EXECUTIONS_INJECTION_KEY = Symbol("subflows-executions-injection-key") as InjectionKey<ComputedRef<Record<string, GraphExecution>>>
export const LOD_INJECTION_KEY = Symbol("lod-injection-key") as InjectionKey<ComputedRef<LodLevel>>
export const VALIDATION_ISSUES_INJECTION_KEY = Symbol("validation-issues-injection-key") as InjectionKey<ComputedRef<Map<string, string[]>>>
export const FOCUSED_TASK_INJECTION_KEY = Symbol("focused-task-injection-key") as InjectionKey<ComputedRef<string | undefined>>
export const DROP_EDGE_INJECTION_KEY = Symbol("drop-edge-injection-key") as InjectionKey<ComputedRef<string | undefined>>
export const DRAGGING_NODE_INJECTION_KEY = Symbol("dragging-node-injection-key") as InjectionKey<ComputedRef<boolean>>
export const CANVAS_HOVERED_INJECTION_KEY = Symbol("canvas-hovered-injection-key") as InjectionKey<ComputedRef<boolean>>
export const LONGEST_TASK_RUN_DURATION_INJECTION_KEY = Symbol("longest-task-run-duration-injection-key") as InjectionKey<ComputedRef<number>>
