// eslint-disable-next-line no-restricted-imports -- this is the typed re-export the rule points to
import {DynamicScroller as UntypedDynamicScroller} from "vue-virtual-scroller"
import type {RecycleScroller} from "vue-virtual-scroller"
import type {HTMLAttributes} from "vue"
import type {NativeAttributes} from "@kestra-io/design-system"

type UndeclaredForwardedProps = Pick<NonNullable<Parameters<typeof RecycleScroller<unknown>>[0]>, "buffer" | "prerender" | "emitUpdate" | "updateInterval"> & NativeAttributes<HTMLAttributes> & {
    onUpdate?: (startIndex: number, endIndex: number, visibleStartIndex: number, visibleEndIndex: number) => void
    onScrollEnd?: () => void
    onScroll?: (event: Event) => void
}
type Rest<TItem> = Parameters<typeof UntypedDynamicScroller<TItem>> extends [unknown, ...infer R] ? R : never

export const DynamicScroller = UntypedDynamicScroller as <TItem>(
    props: Parameters<typeof UntypedDynamicScroller<TItem>>[0] & UndeclaredForwardedProps,
    ...rest: Rest<TItem>
) => ReturnType<typeof UntypedDynamicScroller<TItem>> & {__ctx?: {props: UndeclaredForwardedProps}}

export {DynamicScrollerItem} from "vue-virtual-scroller"
export type {DynamicScrollerExposed} from "vue-virtual-scroller"
