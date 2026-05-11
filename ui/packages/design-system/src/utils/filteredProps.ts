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
