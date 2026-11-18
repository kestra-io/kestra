import type {RouteRecordRaw} from "vue-router"

export const NAMESPACE_PARENT_ROUTE = "namespaces/update"

/**
 * Full universe of namespace tab names across OSS and EE (mirrors the `ORDER`
 * array in components/namespaces/utils/useHelpers.ts). Kept as a plain literal
 * here — rather than importing `ORDER` — so routes.ts doesn't eagerly pull in
 * every namespace tab component (useHelpers.ts imports them at module scope).
 */
export const NAMESPACE_TAB_NAMES = [
    "blueprints",
    "overview",
    "edit",
    "flows",
    "executions",
    "dependencies",
    "secrets",
    "credentials",
    "assets",
    "variables",
    "policies",
    "kv",
    "reusable-inputs",
    "files",
    "history",
    "audit-logs",
]

const TAB_DOC_IDS: Record<string, string> = {
    blueprints: "blueprints",
    overview: "namespaces",
    edit: "namespace.management",
    flows: "flows",
    executions: "executions",
    dependencies: "namespaces",
    secrets: "secrets",
    credentials: "credentials",
    assets: "asset",
    variables: "variables",
    policies: "policies",
    kv: "kv",
    "reusable-inputs": "reusable-inputs",
    files: "files",
    history: "namespace.management",
    "audit-logs": "auditlogs",
}

/**
 * Namespace tabs render via the vertical sidebar (RouteTabsSidebar + Tabs.vue's
 * `TabBody`), not `<router-view>`, so these children carry no `component` — they
 * only give each tab a real, named child route for URLs/links/ctrl-click.
 */
export function createNamespaceTabRoutes(names: string[] = NAMESPACE_TAB_NAMES): RouteRecordRaw[] {
    // These children render nothing themselves (see module doc above), so they match
    // none of vue-router's RouteRecordRaw variants, which all require a component,
    // children, or a redirect — cast past that since it's still a valid route record.
    return names.map((name) => ({
        name: `${NAMESPACE_PARENT_ROUTE}/${name}`,
        path: name,
        meta: {tab: name, docId: TAB_DOC_IDS[name]},
    })) as unknown as RouteRecordRaw[]
}
