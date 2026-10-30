import type {Component, InjectionKey, Ref} from "vue"

/** Card footprint, also inlined onto the canvas root as CSS variables so the layout and CSS cannot drift. */
export const DAG_CARD = {width: 240, height: 64} as const

// Injected rather than passed through node `data`, so changing any of them never rebuilds the node array.
export const DAG_SELECTED = Symbol("dagSelected") as InjectionKey<Ref<string | undefined>>
export const DAG_HOVERED = Symbol("dagHovered") as InjectionKey<Ref<string | undefined>>
export const DAG_TRACED = Symbol("dagTraced") as InjectionKey<Ref<Set<string> | null>>
export const DAG_SHOWN = Symbol("dagShown") as InjectionKey<Ref<Set<string> | null>>

/** Extra card height an artifact footer reserves, applied to every card of the graph. */
export const DAG_FOOTER_HEIGHT = Symbol("dagFooterHeight") as InjectionKey<Ref<number>>
/** Renders the plugin-provided footer for an asset, or nothing when its producer ships none. */
export const DAG_CARD_FOOTER = Symbol("dagCardFooter") as InjectionKey<Component>
