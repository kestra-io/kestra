import type {RouteRecordRaw} from "vue-router"

/**
 * Returns `requested` if it names a real tab in `tabRoutes` (by `meta.tab`),
 * otherwise `fallback`. Guards a parent route's `redirect` against a stale
 * `:tab` param or Settings-stored default that no longer maps to a
 * registered child route, which would otherwise make vue-router throw.
 */
export function resolveDefaultTab(tabRoutes: RouteRecordRaw[], requested: string | null | undefined, fallback: string): string {
    return tabRoutes.some((tabRoute) => tabRoute.meta?.tab === requested) ? (requested as string) : fallback
}
