import type {RouteRecordRaw} from "vue-router"
// @ts-ignore - no type declarations available for this component
import OnlyLeftMenuLayout from "../components/layout/OnlyLeftMenuLayout.vue"
import FullScreenLayout from "../components/layout/FullScreenLayout.vue"
import Errors from "../components/errors/Errors.vue"
import DemoIAM from "../components/demo/IAM.vue"
import DemoTenants from "../components/demo/Tenants.vue"
import DemoAuditLogs from "../components/demo/AuditLogs.vue"
import DemoInstance from "../components/demo/Instance.vue"
import DemoApps from "../components/demo/Apps.vue"
import DemoTests from "../components/demo/Tests.vue"
import DemoAssets from "../components/demo/Assets.vue"
import DemoCases from "../components/demo/Cases.vue"
import DemoQuotas from "../components/demo/Quotas.vue"
import DemoPolicies from "../components/demo/Policies.vue"
import DemoPromote from "../components/demo/Promote.vue"
import {EXECUTION_ROUTE} from "../components/executions/executionTabs"
import {FLOW_ROUTE} from "../components/flows/flowTabs"
import {NAMESPACE_PARENT_ROUTE, createNamespaceTabRoutes} from "../utils/namespaceTabRoutes"

/** A route record, plus `ossOnly`: editions layering on this table (EE) drop the flagged records. */
export type KestraRouteRecord = RouteRecordRaw & {ossOnly?: boolean}

const routes: KestraRouteRecord[] = [
    //Initial
    {name: "root", path: "/", redirect: {name: "home"}, meta: {layout: {template: "<div />"}, anonymous: true}},

    {name: "ai",path: "/:tenant?/ai", component: () => import("../components/ai/copilot/CopilotPage.vue")},

    //Dashboards
    {
        name: "home",
        path: "/:tenant?/dashboards/:dashboard?",
        component: () => import("../components/dashboard/Dashboard.vue"),
    },
    {name: "dashboards/create", path: "/:tenant?/dashboards/new", component: () => import("../components/dashboard/components/Create.vue")},
    {name: "dashboards/update", path: "/:tenant?/dashboards/:dashboard/edit", component: () => import("override/components/dashboard/Edit.vue")},

    //Flows
    {
        name: "flows/list",
        path: "/:tenant?/flows",
        component: () => import("../components/flows/Flows.vue"),
    },
    {name: "flows/search", path: "/:tenant?/flows/search", component: () => import("../components/flows/FlowsSearch.vue")},
    {name: "flows/create", path: "/:tenant?/flows/new", component: () => import("../components/flows/FlowCreate.vue")},
    FLOW_ROUTE,

    //Executions
    {
        name: "executions/list",
        path: "/:tenant?/executions",
        component: () => import("../components/executions/Executions.vue"),
    },
    EXECUTION_ROUTE,

    //KV
    {name: "kv/list", path: "/:tenant?/kv", component: () => import("../components/kv/KVs.vue")},

    //Secrets
    {name: "secrets/list", path: "/:tenant?/secrets", component: () => import("../components/secrets/Secrets.vue")},

    //Blueprints
    {name: "blueprints", path: "/:tenant?/blueprints/:kind/:tab", component: () => import("override/components/flows/blueprints/Blueprints.vue"), props: true},
    {name: "blueprints/view", path: "/:tenant?/blueprints/:kind/:tab/:blueprintId", component: () => import("override/components/flows/blueprints/BlueprintDetail.vue"), props: true},

    //Documentation
    {name: "plugins/list", path: "/:tenant?/plugins", component: () => import("../components/plugins/PluginCatalog.vue")},
    {name: "plugins/group", path: "/:tenant?/plugins/groups/:name/:subGroup?", component: () => import("../components/plugins/PluginGroup.vue")},
    {name: "plugins/view", path: "/:tenant?/plugins/:cls/:version?",   component: () => import("../components/plugins/PluginDetail.vue")},

    //Logs
    {
        name: "logs/list",
        path: "/:tenant?/logs",
        component: () => import("../components/logs/LogsWrapper.vue"),
    },

    //Namespaces
    {name: "namespaces/list", path: "/:tenant?/namespaces", component: () => import("override/components/namespaces/Namespaces.vue")},
    {
        name: NAMESPACE_PARENT_ROUTE,
        path: "/:tenant?/namespaces/edit/:id",
        component: () => import("../components/namespaces/Namespace.vue"),
        // Resolve legacy deep-links `{name: "namespaces/update", params: {tab}}` and bare
        // `/:id` URLs to the matching child route, preserving params and query.
        redirect: (to) => {
            const tab = (to.params.tab as string) || "overview"
            return {name: `${NAMESPACE_PARENT_ROUTE}/${tab}`, params: to.params, query: to.query}
        },
        children: createNamespaceTabRoutes(),
    },

    //Docs
    {name: "docs/view", path: "/:tenant?/docs/:path(.*)?", component: () => import("../components/docs/Docs.vue"), meta: {layout: OnlyLeftMenuLayout}},

    //Settings
    {name: "preferences", path: "/:tenant?/preferences", component: () => import("override/components/settings/Settings.vue")},

    //Admin
    {name: "admin/triggers", path: "/:tenant?/admin/triggers/:tab?", component: () => import("../components/admin/triggers/Triggers.vue")},
    {name: "admin/stats", path: "/:tenant?/admin/stats/:type?", component: () => import("override/components/admin/stats/Stats.vue")},
    {name: "admin/concurrency-limits", path: "/:tenant?/admin/concurrency-limits", component: () => import("../components/admin/ConcurrencyLimits.vue")},
    {name: "admin/mcp-servers",        path: "/:tenant?/admin/mcp-servers",                         component: () => import("../components/admin/McpServerList.vue")},
    {name: "admin/mcp-servers/update", path: "/:tenant?/admin/mcp-servers/edit/:id/:tab?",            component: () => import("../components/admin/McpServer.vue")},
    {name: "admin/mcp-servers/create", path: "/:tenant?/admin/mcp-servers/new/:tab?",                 component: () => import("../components/admin/McpServer.vue")},

    //Setup
    // ossOnly: posts to /api/v1/{tenant}/basicAuth, which EE does not implement.
    {name: "setup", path: "/:tenant?/setup", component: () => import("../components/basicauth/BasicAuthSetup.vue"), meta: {layout: FullScreenLayout, anonymous: true}, ossOnly: true},
    //Login
    {name: "login", path: "/:tenant?/login", component: () => import("../components/basicauth/BasicAuthLogin.vue"), meta: {layout: FullScreenLayout, anonymous: true}},

    //Errors
    {name: "errors/404-wildcard", path: "/:tenant?/:pathMatch(.*)", component: Errors, props: {code: 404}},

    //Demo Pages
    {name: "apps/list", path: "/:tenant?/apps", component: DemoApps},
    {name: "tests/list", path: "/:tenant?/tests", component: DemoTests},
    {name: "assets/list", path: "/:tenant?/assets", component: DemoAssets},
    {name: "cases/list", path: "/:tenant?/cases", component: DemoCases},
    {name: "admin/iam", path: "/:tenant?/admin/iam", component: DemoIAM},
    {name: "admin/tenants/list", path: "/:tenant?/admin/tenants/list", component: DemoTenants},
    {name: "admin/auditlogs/list", path: "/:tenant?/admin/auditlogs", component: DemoAuditLogs},
    {name: "admin/quotas/list", path: "/:tenant?/admin/quotas", component: DemoQuotas},
    {name: "admin/policies", path: "/:tenant?/admin/policies", component: DemoPolicies},
    {name: "admin/instance", path: "/:tenant?/admin/instance", component: DemoInstance},
    {name: "promote/targets", path: "/:tenant?/promote/targets", component: DemoPromote},
]

export default routes
