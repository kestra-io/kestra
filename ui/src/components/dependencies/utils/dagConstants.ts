import type {InjectionKey, Ref} from "vue"

/**
 * Card footprint in layout coordinates. Inlined onto the canvas root as
 * `--ks-dag-card-width` / `--ks-dag-card-height` so the CSS and the layout cannot
 * drift apart the way topology's NODE_SIZES and its stylesheet literals do.
 */
export const DAG_CARD = {width: 240, height: 64} as const

/**
 * Zoom thresholds for the compact card. The gap between them is hysteresis: a
 * single threshold makes a pinch that settles on it flip back and forth.
 */
export const DAG_LOD = {toCompact: 0.55, toFull: 0.7} as const

export type DagDetail = "full" | "compact"

/** Selection, hover, dim and detail reach the cards by injection rather than through
 * node `data`, so changing any of them never rebuilds the node array. */
export const DAG_SELECTED = Symbol("dagSelected") as InjectionKey<Ref<string | undefined>>
export const DAG_HOVERED = Symbol("dagHovered") as InjectionKey<Ref<string | undefined>>
export const DAG_TRACED = Symbol("dagTraced") as InjectionKey<Ref<Set<string> | null>>
export const DAG_DIMMED = Symbol("dagDimmed") as InjectionKey<Ref<Set<string> | null>>
export const DAG_DETAIL = Symbol("dagDetail") as InjectionKey<Ref<DagDetail>>
