import {inject, provide, readonly, ref, type InjectionKey, type Ref} from "vue"
import {useElementSize} from "@vueuse/core"

const editorHeaderWidthKey = Symbol("editorHeaderWidth") as InjectionKey<Readonly<Ref<number>>>

const UNCONSTRAINED = Number.POSITIVE_INFINITY

export const EDITOR_HEADER_BREAKPOINTS = {
    iconOnlyControls: 1000,
    tabsAsDropdown: 500,
} as const

export function provideEditorHeaderWidth(element: Ref<HTMLElement | undefined>): Readonly<Ref<number>> {
    const {width} = useElementSize(element, {width: UNCONSTRAINED, height: 0}, {box: "border-box"})
    const shared = readonly(width)
    provide(editorHeaderWidthKey, shared)
    return shared
}

export function useEditorHeaderWidth(): Readonly<Ref<number>> {
    return inject(editorHeaderWidthKey, ref(UNCONSTRAINED))
}
