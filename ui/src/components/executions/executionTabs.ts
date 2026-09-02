import type {RouteMeta, RouteRecordRaw} from "vue-router"
import {resolveDefaultTab} from "../../utils/routeTabs"
import {ENTITY_REQUEST_OPTIONS} from "../../utils/routeEntityGuard"

/** Parent route name for the Executions detail page. */
export const EXECUTION_PARENT_ROUTE = "executions/update"

/** localStorage key remembering the last tab the user viewed, used as the redirect fallback below. */
export const DEFAULT_TAB_STORAGE_KEY = "executeDefaultTab"

/** Where an execution opens with no preference set. Shared by the redirect, `submitTask` and Settings. */
export const DEFAULT_EXECUTION_TAB = "gantt"

/**
 * Single source of truth for the Executions detail tabs.
 *
 * Each entry is the vue-router child route that `<router-view>` renders; the
 * horizontal tab bar is derived from these records (see {@link useExecutionRoot}),
 * so tabs are defined once and colocated with the routes they map to.
 *
 * - `path` equals the tab token, keeping URLs byte-identical to the legacy
 *   `:tab` param.
 * - `meta.tab` is the tab token (resolved by `useActiveTab`).
 * - `meta.title` is the i18n key resolved to the bar label.
 * - `meta.maximized` / `meta.noOverflow` drive the content section layout.
 * - `meta.locked` flags an Enterprise-locked tab (lock badge in the bar).
 */
export const EXECUTION_TAB_ROUTES: RouteRecordRaw[] = [
    {
        name: `${EXECUTION_PARENT_ROUTE}/overview`,
        path: "overview",
        component: () => import("./overview/Overview.vue"),
        meta: {tab: "overview", title: "overview"},
    },
    {
        name: `${EXECUTION_PARENT_ROUTE}/gantt`,
        path: "gantt",
        component: () => import("./Gantt.vue"),
        meta: {tab: "gantt", title: "gantt"},
    },
    {
        name: `${EXECUTION_PARENT_ROUTE}/logs`,
        path: "logs",
        component: () => import("./Logs.vue"),
        meta: {tab: "logs", title: "logs"},
    },
    {
        name: `${EXECUTION_PARENT_ROUTE}/outputs`,
        path: "outputs",
        component: () => import("./outputs/ExecutionVariableExplorer.vue"),
        meta: {tab: "outputs", title: "variable_explorer.title", maximized: true, noOverflow: true},
    },
    {
        name: `${EXECUTION_PARENT_ROUTE}/metrics`,
        path: "metrics",
        component: () => import("./ExecutionMetric.vue"),
        meta: {tab: "metrics", title: "metrics"},
    },
    {
        name: `${EXECUTION_PARENT_ROUTE}/dependencies`,
        path: "dependencies",
        component: () => import("../dependencies/Dependencies.vue"),
        props: {isReadOnly: true},
        meta: {tab: "dependencies", title: "dependencies", maximized: true},
    },
    {
        name: `${EXECUTION_PARENT_ROUTE}/audit-logs`,
        path: "audit-logs",
        component: () => import("../demo/AuditLogs.vue"),
        meta: {tab: "audit-logs", title: "auditlogs", maximized: true, locked: true},
    },
    {
        name: `${EXECUTION_PARENT_ROUTE}/assets`,
        path: "assets",
        component: () => import("../demo/Assets.vue"),
        props: {topbar: false},
        meta: {tab: "assets", title: "assets.title", maximized: true, locked: true},
    },
]

/**
 * Loads the execution the detail page is about into the store (EE reuses this for its own route
 * record). The page itself only learns of a missing execution when its SSE stream fails, which
 * cannot tell "not found" from "connection lost"; and what the guard loads is what the overview
 * tab would have fetched, so the page renders from the store instead of fetching it again.
 */
export const EXECUTION_ENTITY_META: RouteMeta = {
    entity: async (to) => {
        const {useExecutionsStore} = await import("../../stores/executions")
        return useExecutionsStore().loadExecution({id: String(to.params.id)}, ENTITY_REQUEST_OPTIONS)
    },
}

/**
 * The Executions detail page's own route: parent + children, colocated with the tab
 * definitions above so this page owns its full routing structure end to end.
 */
export const EXECUTION_ROUTE: RouteRecordRaw = {
    name: EXECUTION_PARENT_ROUTE,
    path: "/:tenant?/executions/:namespace/:flowId/:id",
    component: () => import("./ExecutionRoot.vue"),
    meta: EXECUTION_ENTITY_META,
    // Resolve legacy deep-links `{name: "executions/update", params: {tab}}` and bare
    // `/:id` URLs to the matching child route, preserving params and query.
    redirect: (to) => {
        const requested = (to.params.tab as string) || localStorage.getItem(DEFAULT_TAB_STORAGE_KEY)
        const tab = resolveDefaultTab(EXECUTION_TAB_ROUTES, requested, DEFAULT_EXECUTION_TAB)
        return {name: `${EXECUTION_PARENT_ROUTE}/${tab}`, params: to.params, query: to.query}
    },
    children: EXECUTION_TAB_ROUTES,
}
