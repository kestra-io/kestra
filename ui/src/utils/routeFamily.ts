/**
 * Route "families" migrated from a flat `:tab?` param to vue-router children
 * (see e.g. flowTabs.ts, executionTabs.ts, and kestra-ee's assetTabs.ts). Once
 * migrated, the active route's name becomes `<family>/<tab>` instead of the
 * flat `<family>` — this helper normalizes back to the flat family name so
 * route-identity checks written against the flat name keep working whether
 * the page has been migrated to children or not.
 */
const MIGRATED_ROUTE_FAMILIES = ["executions/update", "flows/update", "assets/update"]

export function routeFamily(routeName: unknown): string {
    const name = String(routeName ?? "")
    return MIGRATED_ROUTE_FAMILIES.find((family) => name === family || name.startsWith(`${family}/`)) ?? name
}
