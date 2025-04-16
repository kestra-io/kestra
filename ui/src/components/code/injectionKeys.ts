import {InjectionKey} from "vue"

export const FLOW_INJECTION_KEY = Symbol("flow-injection-key") as InjectionKey<string>
export const CREATING_INJECTION_KEY = Symbol("creating-injection-key") as InjectionKey<boolean>
export const SAVEMODE_INJECTION_KEY = Symbol("flow-id-injection-key") as InjectionKey<"button" | "auto">