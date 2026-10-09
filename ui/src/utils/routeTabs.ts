import type {RouteRecordRaw} from "vue-router"

/**
 * Returns `requested` if it names a real tab in `tabRoutes` (by `meta.tab`),
 * otherwise `fallback`. Guards a parent route's `redirect` against a stale
 * `:tab` param or Settings-stored default that no longer maps to a
 * registered child route, which would otherwise make vue-router throw.
 */
export function resolveDefaultTab(tabRoutes: RouteRecordRaw[], requested: string | null | undefined, fallback: string): string {
    // A nullish `requested` would otherwise match a route without `meta.tab` (`undefined === undefined`).
    return requested != null && tabRoutes.some((tabRoute) => tabRoute.meta?.tab === requested) ? requested : fallback
}

/** An extra tab sharing a `meta.tab` with a base tab replaces it in place; the others are appended. */
export function mergeTabRoutes(baseTabRoutes: RouteRecordRaw[], extraTabRoutes: RouteRecordRaw[]): RouteRecordRaw[] {
    const tabKey = (tabRoute: RouteRecordRaw) => tabRoute.meta?.tab as string

    const extraByTabKey = new Map(extraTabRoutes.map((tabRoute) => [tabKey(tabRoute), tabRoute]))
    const baseTabKeys = new Set(baseTabRoutes.map(tabKey))

    const replaced = baseTabRoutes.map((tabRoute) => extraByTabKey.get(tabKey(tabRoute)) ?? tabRoute)
    const appended = extraTabRoutes.filter((tabRoute) => !baseTabKeys.has(tabKey(tabRoute)))

    return [...replaced, ...appended]
}
