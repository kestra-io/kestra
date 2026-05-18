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

export default [
    //Initial
    {name: "root", path: "/", redirect: {name: "home"}, meta: {layout: {template: "<div />"}, anonymous: true}},

    // New onboarding pages, initial one and the success one after the user has completed the onboarding flow.
    {name: "welcome", path: "/:tenant?/welcome", component: () => import("../components/onboarding/Welcome.vue")},
    {name: "welcome/success", path: "/:tenant?/welcome/success", component: () => import("../components/onboarding/Success.vue")},

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
    {name: "flows/update", path: "/:tenant?/flows/edit/:namespace/:id/:tab?", component: () => import("../components/flows/FlowRoot.vue")},

    //Executions
    {
        name: "executions/list",
        path: "/:tenant?/executions",
        component: () => import("../components/executions/Executions.vue"),
    },
    {name: "executions/update", path: "/:tenant?/executions/:namespace/:flowId/:id/:tab?", component: () => import("../components/executions/ExecutionRoot.vue")},

    //KV
    {name: "kv/list", path: "/:tenant?/kv", component: () => import("../components/kv/KVs.vue")},

    //Secrets
    {name: "secrets/list", path: "/:tenant?/secrets", component: () => import("../components/secrets/Secrets.vue")},

    //Blueprints
    {name: "blueprints", path: "/:tenant?/blueprints/:kind/:tab", component: () => import("override/components/flows/blueprints/Blueprints.vue"), props: true},
    {name: "blueprints/view", path: "/:tenant?/blueprints/:kind/:tab/:blueprintId", component: () => import("override/components/flows/blueprints/BlueprintDetail.vue"), props: true},

    //Documentation
    {name: "plugins/list", path: "/:tenant?/plugins", component: () => import("../components/plugins/Plugin.vue")},
    {name: "plugins/view", path: "/:tenant?/plugins/:cls/:version?",   component: () => import("../components/plugins/Plugin.vue")},

    //Logs
    {
        name: "logs/list",
        path: "/:tenant?/logs",
        component: () => import("../components/logs/LogsWrapper.vue"),
    },

    //Namespaces
    {name: "namespaces/list", path: "/:tenant?/namespaces", component: () => import("override/components/namespaces/Namespaces.vue")},
    {name: "namespaces/update", path: "/:tenant?/namespaces/edit/:id/:tab?", component: () => import("../components/namespaces/Namespace.vue")},

    //Docs
    {name: "docs/view", path: "/:tenant?/docs/:path(.*)?", component: () => import("../components/docs/Docs.vue"), meta: {layout: OnlyLeftMenuLayout}},

    //Settings
    {name: "settings", path: "/:tenant?/settings", component: () => import("override/components/settings/Settings.vue")},

    //Admin
    {name: "admin/triggers", path: "/:tenant?/admin/triggers/:tab?", component: () => import("../components/admin/triggers/Triggers.vue")},
    {name: "admin/stats", path: "/:tenant?/admin/stats/:type?", component: () => import("override/components/admin/stats/Stats.vue")},
    {name: "admin/concurrency-limits", path: "/:tenant?/admin/concurrency-limits", component: () => import("../components/admin/ConcurrencyLimits.vue")},
    {name: "admin/mcp-servers", path: "/:tenant?/admin/mcp-servers", component: () => import("../components/admin/McpServers.vue")},

    //Setup
    {name: "setup", path: "/:tenant?/setup", component: () => import("../components/basicauth/BasicAuthSetup.vue"), meta: {layout: FullScreenLayout, anonymous: true}},
    //Login
    {name: "login", path: "/:tenant?/login", component: () => import("../components/basicauth/BasicAuthLogin.vue"), meta: {layout: FullScreenLayout, anonymous: true}},

    //Errors
    {name: "errors/404-wildcard", path: "/:tenant?/:pathMatch(.*)", component: Errors, props: {code: 404}},

    //Demo Pages
    {name: "apps/list", path: "/:tenant?/apps", component: DemoApps},
    {name: "tests/list", path: "/:tenant?/tests", component: DemoTests},
    {name: "assets/list", path: "/:tenant?/assets", component: DemoAssets},
    {name: "admin/iam", path: "/:tenant?/admin/iam", component: DemoIAM},
    {name: "admin/tenants/list", path: "/:tenant?/admin/tenants/list", component: DemoTenants},
    {name: "admin/auditlogs/list", path: "/:tenant?/admin/auditlogs", component: DemoAuditLogs},
    {name: "admin/instance", path: "/:tenant?/admin/instance", component: DemoInstance},
]
