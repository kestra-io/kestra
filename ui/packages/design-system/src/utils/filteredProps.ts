import {camelize, getCurrentInstance} from "vue"

/**
 * Returns a function that filters out undefined values from props,
 * so only explicitly provided props are forwarded to inner components.
 *
 * This prevents Vue's boolean prop casting (absent boolean props are cast to `false`,
 * overriding element-plus defaults that may be `true`).
 */
export function useFilteredProps<T extends Record<string, unknown>>(props: T, skip?: (keyof T)[]): () => Partial<T> {
    return () => Object.fromEntries(
        Object.entries(props).filter(([k, v]) => v !== undefined && !skip?.includes(k as keyof T)),
    ) as Partial<T>
}

/**
 * Like useFilteredProps, but forwards only the props the parent actually passed, so a boolean Vue cast to
 * `false` never overrides an element-plus default of `true` or `undefined` (`validateEvent`, `teleported`).
 */
export function usePassedProps<T extends Record<string, unknown>>(props: T, skip?: (keyof T)[]): () => Partial<T> {
    const instance = getCurrentInstance()
    return () => {
        const passed = new Set(Object.keys(instance?.vnode.props ?? {}).map(camelize))
        return Object.fromEntries(
            Object.entries(props).filter(([k, v]) => passed.has(k) && v !== undefined && !skip?.includes(k as keyof T)),
        ) as Partial<T>
    }
}
