import type {RouteRecordRaw} from "vue-router"
import resource from "../../models/resource"
import action from "../../models/action"
import {resolveDefaultTab} from "../../utils/routeTabs"

/** Parent route name for the Flows detail page. */
export const FLOW_PARENT_ROUTE = "flows/update"

/**
 * Shared visibility predicate for a Flows detail tab, driven by the current user's
 * permissions on the flow's namespace. Colocated here (rather than duplicated in
 * `useFlowRoot` and kestra-ee's `FlowRoot.vue`) so both repos stay in sync.
 */
export function isFlowTabAllowed(tabName: string, ctx: {user: any; namespace: string | undefined}): boolean {
    const {user, namespace} = ctx
    switch (tabName) {
    case "overview":
        return !!user?.hasAny(resource.EXECUTION)
    case "executions":
    case "logs":
    case "metrics":
        return !!(user && namespace && user.isAllowed(resource.EXECUTION, action.VIEW, namespace))
    case "edit":
    case "revisions":
    case "triggers":
    case "dependencies":
        return !!(user && namespace && user.isAllowed(resource.FLOW, action.VIEW, namespace))
    default:
        return true
    }
}

/** localStorage key remembering the user's preferred default tab (see BasicSettings.vue), used as the redirect fallback below. */
const DEFAULT_TAB_STORAGE_KEY = "flowDefaultTab"

/**
 * Single source of truth for the Flows detail tabs.
 *
 * Each entry is the vue-router child route that `<router-view>` renders; the
 * horizontal tab bar is derived from these records (see {@link useFlowRoot}),
 * so tabs are defined once and colocated with the routes they map to.
 *
 * - `path` equals the tab token, keeping URLs byte-identical to the legacy
 *   `:tab` param.
 * - `meta.tab` is the tab token (resolved by `useActiveTab`).
 * - `meta.title` is the i18n key resolved to the bar label.
 * - `meta.maximized` drives the content section layout.
 * - `meta.locked` flags an Enterprise-locked tab (lock badge in the bar).
 */
export const FLOW_TAB_ROUTES: RouteRecordRaw[] = [
    {
        name: `${FLOW_PARENT_ROUTE}/overview`,
        path: "overview",
        component: () => import("./Overview.vue"),
        meta: {tab: "overview", title: "overview"},
    },
    {
        name: `${FLOW_PARENT_ROUTE}/executions`,
        path: "executions",
        component: () => import("./FlowExecutions.vue"),
        props: {embed: true},
        meta: {tab: "executions", title: "executions"},
    },
    {
        name: `${FLOW_PARENT_ROUTE}/edit`,
        path: "edit",
        component: () => import("./MultiPanelFlowEditorView.vue"),
        meta: {tab: "edit", title: "edit", maximized: true},
    },
    {
        name: `${FLOW_PARENT_ROUTE}/promote`,
        path: "promote",
        component: () => import("../demo/Promote.vue"),
        props: {embed: true},
        meta: {tab: "promote", title: "promote.label", locked: true},
    },
    {
        name: `${FLOW_PARENT_ROUTE}/revisions`,
        path: "revisions",
        component: () => import("./FlowRevisions.vue"),
        meta: {tab: "revisions", title: "revisions"},
    },
    {
        name: `${FLOW_PARENT_ROUTE}/triggers`,
        path: "triggers",
        component: () => import("./FlowTriggers.vue"),
        meta: {tab: "triggers", title: "triggers"},
    },
    {
        name: `${FLOW_PARENT_ROUTE}/logs`,
        path: "logs",
        component: () => import("../logs/LogsWrapper.vue"),
        props: {showFilters: true, restoreurl: false},
        meta: {tab: "logs", title: "logs"},
    },
    {
        name: `${FLOW_PARENT_ROUTE}/metrics`,
        path: "metrics",
        component: () => import("./FlowMetrics.vue"),
        meta: {tab: "metrics", title: "metrics"},
    },
    {
        name: `${FLOW_PARENT_ROUTE}/dependencies`,
        path: "dependencies",
        component: () => import("../dependencies/Dependencies.vue"),
        props: {isReadOnly: true},
        meta: {tab: "dependencies", title: "dependencies", maximized: true},
    },
    {
        name: `${FLOW_PARENT_ROUTE}/concurrency`,
        path: "concurrency",
        component: () => import("./FlowConcurrency.vue"),
        meta: {tab: "concurrency", title: "concurrency"},
    },
    {
        name: `${FLOW_PARENT_ROUTE}/audit-logs`,
        path: "audit-logs",
        component: () => import("../demo/AuditLogs.vue"),
        props: {embed: true},
        meta: {tab: "audit-logs", title: "auditlogs", locked: true},
    },
]

/**
 * The Flows detail page's own route: parent + children, colocated with the tab
 * definitions above so this page owns its full routing structure end to end.
 */
export const FLOW_ROUTE: RouteRecordRaw = {
    name: FLOW_PARENT_ROUTE,
    path: "/:tenant?/flows/edit/:namespace/:id",
    component: () => import("./FlowRoot.vue"),
    // Resolve legacy deep-links `{name: "flows/update", params: {tab}}` and bare
    // `/:id` URLs to the matching child route, preserving params and query.
    redirect: (to) => {
        const requested = (to.params.tab as string) || localStorage.getItem(DEFAULT_TAB_STORAGE_KEY)
        const tab = resolveDefaultTab(FLOW_TAB_ROUTES, requested, "edit")
        return {name: `${FLOW_PARENT_ROUTE}/${tab}`, params: to.params, query: to.query}
    },
    children: FLOW_TAB_ROUTES,
}
