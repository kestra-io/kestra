import type {ComputedRef, InjectionKey, Ref} from "vue"

export const FLOW_INJECTION_KEY = Symbol("flow-injection-key") as InjectionKey<ComputedRef<string>>
export const SECTION_INJECTION_KEY = Symbol("section-injection-key") as InjectionKey<Ref<string>>
export const TASKID_INJECTION_KEY = Symbol("taskid-injection-key") as InjectionKey<Ref<string>>
export const POSITION_INJECTION_KEY = Symbol("position-injection-key") as InjectionKey<"after" | "before">
export const CREATING_INJECTION_KEY = Symbol("creating-injection-key") as InjectionKey<Ref<boolean>>
export const SAVEMODE_INJECTION_KEY = Symbol("flow-id-injection-key") as InjectionKey<"button" | "auto">