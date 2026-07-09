import {defineComponent, type Component, type InjectionKey} from "vue"

/**
 * KsEditor renders a real plugin task icon in Monaco's autocomplete suggestion
 * rows, but the icon component itself lives in the consuming app (not the
 * design system) since it depends on app-specific plugin-icon APIs. The app
 * provides its icon component through this injection key; KsEditor falls back
 * to `EmptyTaskIcon` (renders nothing) when nothing was provided.
 */
export const EDITOR_TASK_ICON_INJECTION_KEY: InjectionKey<Component> = Symbol("editor-task-icon-injection-key")

export const EmptyTaskIcon = defineComponent({
    name: "EmptyTaskIcon",
    inheritAttrs: false,
    render: () => null,
})
