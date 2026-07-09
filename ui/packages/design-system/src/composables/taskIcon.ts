import {defineComponent, h, inject, type Component, type InjectionKey, type PropType} from "vue"
import fallbackIcon from "../assets/images/plugin-icon-fallback.svg"

/**
 * The real task-icon component lives in the consuming app (not the design
 * system) since it depends on app-specific plugin-icon APIs. The app provides
 * it once, at bootstrap, through TASK_ICON_INJECTION_KEY; every design-system
 * consumer that needs to render a task icon (KsEditor's Monaco suggestions,
 * the topology package's graph nodes) resolves the same instance through
 * useTaskIcon() instead of importing an app component directly. This is the
 * contract that component must fulfil.
 */
export interface TaskIconProps {
    cls?: string;
    customIcon?: {icon: string; monochrome?: boolean};
    icons?: Record<string, any>;
    onlyIcon?: boolean;
    variable?: string;
    loadIcon?: (cls: string) => Promise<any>;
}

export const TASK_ICON_INJECTION_KEY: InjectionKey<Component<TaskIconProps>> = Symbol("task-icon-injection-key")

const FallbackTaskIcon = defineComponent({
    name: "FallbackTaskIcon",
    inheritAttrs: false,
    props: {
        cls: {type: String, default: undefined},
        customIcon: {type: Object as PropType<TaskIconProps["customIcon"]>, default: undefined},
        icons: {type: Object as PropType<TaskIconProps["icons"]>, default: undefined},
        onlyIcon: {type: Boolean, default: false},
        variable: {type: String, default: undefined},
        loadIcon: {type: Function as PropType<TaskIconProps["loadIcon"]>, default: undefined},
    },
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
