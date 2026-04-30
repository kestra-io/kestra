import type {ComputedRef, InjectionKey, Ref} from "vue"

export const EXECUTION_INJECTION_KEY = Symbol("execution-injection-key") as InjectionKey<ComputedRef<any>>
export const SUBFLOWS_EXECUTIONS_INJECTION_KEY = Symbol("subflows-executions-injection-key") as InjectionKey<ComputedRef<Record<string, any[]>>>
export const SHOW_EXTRA_DETAILS_INJECTION_KEY = Symbol("show-extra-details-injection-key") as InjectionKey<Ref<boolean>>
