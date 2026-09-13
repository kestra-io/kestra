import type {InjectionKey, Ref} from "vue"

/**
 * Value -> CSS color string (any valid `color:` value: hex, rgb(), var(--token), ...).
 * Provided by KsSelect, injected by descendant KsOption to color both the dropdown
 * row and (via KsSelect's own #label fallback) the collapsed selected value.
 */
export type KsSelectColorMap = Record<string, string>

export const KsSelectColorMapKey: InjectionKey<Ref<KsSelectColorMap | undefined>> = Symbol("KsSelectColorMap")
