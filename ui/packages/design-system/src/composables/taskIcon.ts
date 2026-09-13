import {defineComponent, h, inject, type Component, type ExtractPublicPropTypes, type InjectionKey, type PropType} from "vue"
import fallbackIcon from "../assets/images/plugin-icon-fallback.svg"

/**
 * The contract any injected task-icon component must fulfil. Declared once as a
 * runtime props definition so FallbackTaskIcon and the TaskIconProps type stay
 * in sync — the type is induced from this object rather than duplicated.
 */
export const taskIconProps = {
    cls: {type: String, default: undefined},
    customIcon: {type: Object as PropType<{icon: string; monochrome?: boolean}>, default: undefined},
    icons: {type: Object as PropType<Record<string, any>>, default: undefined},
    onlyIcon: {type: Boolean, default: false},
    variable: {type: String, default: undefined},
    loadIcon: {type: Function as PropType<(cls: string) => Promise<any>>, default: undefined},
} as const

export type TaskIconProps = ExtractPublicPropTypes<typeof taskIconProps>

/**
 * The real task-icon component lives in the consuming app (not the design
 * system) since it depends on app-specific plugin-icon APIs. The app provides
 * it once, at bootstrap, through TASK_ICON_INJECTION_KEY; every design-system
 * consumer that needs to render a task icon (KsEditor's Monaco suggestions,
 * the topology package's graph nodes) resolves the same instance through
 * useTaskIcon() instead of importing an app component directly.
 */
export const TASK_ICON_INJECTION_KEY: InjectionKey<Component<TaskIconProps>> = Symbol("task-icon-injection-key")

const FallbackTaskIcon = defineComponent({
    name: "FallbackTaskIcon",
    inheritAttrs: false,
    props: taskIconProps,
    render: () => h("img", {src: fallbackIcon, alt: "icon", style: {width: "100%", height: "100%", objectFit: "contain"}}),
})

/**
 * Resolves the app-provided task-icon component, falling back to the generic
 * placeholder icon when the app didn't provide one (e.g. in isolated tests or
 * Storybook).
 */
export function useTaskIcon(): Component<TaskIconProps> {
    return inject(TASK_ICON_INJECTION_KEY, FallbackTaskIcon)
}
