import type {InjectionKey} from "vue"

export const asItemKey = Symbol("asItem") as InjectionKey<boolean>

export const closeDropdownKey = Symbol("closeDropdown") as InjectionKey<() => void>
