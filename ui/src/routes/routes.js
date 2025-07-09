import OnlyLeftMenuLayout from "../components/layout/OnlyLeftMenuLayout.vue"
import FullScreenLayout from "../components/layout/FullScreenLayout.vue"
import Errors from "../components/errors/Errors.vue"
import DemoIAM from "../components/demo/IAM.vue"
import DemoTenants from "../components/demo/Tenants.vue"
import DemoAuditLogs from "../components/demo/AuditLogs.vue"
import DemoInstance from "../components/demo/Instance.vue"
import DemoApps from "../components/demo/Apps.vue"
import DemoTests from "../components/demo/Tests.vue"

export default [
    //Initial
    {name: "root", path: "/", redirect: {name: "home"}, meta: {layout: {template: "<div />"}}},
    {name: "welcome", path: "/:tenant?/welcome", component: () => import("../components/onboarding/Welcome.vue")},

    //Dashboards
    {
        name: "home",
        path: "/:tenant?/dashboards/:dashboard?",
        component: () => import("../components/dashboard/Dashboard.vue"),
        beforeEnter: (to, from, next) => {
            if (!to.params.dashboard) {
                next({
                    name: "home",
                    params: {
                        ...to.params,
                        dashboard: "default",
                    },
                    query: to.query,
                });
            } else {
                next();
            }
        },
    },
    {name: "dashboards/create", path: "/:tenant?/dashboards/new", component: () => import("../components/dashboard/components/Create.vue")},
    {name: "dashboards/update", path: "/:tenant?/dashboards/:dashboard/edit", component: () => import("override/components/dashboard/Edit.vue")},

    //Flows
    {name: "flows/list", path: "/:tenant?/flows", component: () => import("../components/flows/Flows.vue")},
    {name: "flows/search", path: "/:tenant?/flows/search", component: () => import("../components/flows/FlowsSearch.vue")},
    {name: "flows/create", path: "/:tenant?/flows/new", component: () => import("../components/flows/FlowCreate.vue")},
    {name: "flows/update", path: "/:tenant?/flows/edit/:namespace/:id/:tab?", component: () => import("../components/flows/FlowRoot.vue")},

    //Executions
    {name: "executions/list", path: "/:tenant?/executions", component: () => import("../components/executions/Executions.vue")},
    {name: "executions/update", path: "/:tenant?/executions/:namespace/:flowId/:id/:tab?", component: () => import("../components/executions/ExecutionRoot.vue")},

    //TaskRuns
    {name: "taskruns/list", path: "/:tenant?/taskruns", component: () => import("../components/taskruns/TaskRuns.vue")},

    //KV
    {name: "kv/list", path: "/:tenant?/kv", component: () => import("../components/kv/KVs.vue")},

    //Secrets
    {name: "secrets/list", path: "/:tenant?/secrets", component: () => import("../components/secrets/Secrets.vue")},

    //Blueprints
    {name: "blueprints", path: "/:tenant?/blueprints/:kind/:tab", component: () => import("override/components/flows/blueprints/Blueprints.vue"), props: true},
    {name: "blueprints/view", path: "/:tenant?/blueprints/:kind/:tab/:blueprintId", component: () => import("../components/flows/blueprints/BlueprintDetail.vue"), props: true},

    //Documentation
    {name: "plugins/list", path: "/:tenant?/plugins", component: () => import("../components/plugins/Plugin.vue")},
    {name: "plugins/view", path: "/:tenant?/plugins/:cls/:version?",   component: () => import("../components/plugins/Plugin.vue")},

    //Templates
    {name: "templates/list", path: "/:tenant?/templates", component: () => import("../components/templates/Templates.vue")},
    {name: "templates/create", path: "/:tenant?/templates/new", component: () => import("../components/templates/TemplateEdit.vue")},
    {name: "templates/update", path: "/:tenant?/templates/edit/:namespace/:id", component: () => import("../components/templates/TemplateEdit.vue")},

    //Logs
    {name: "logs/list", path: "/:tenant?/logs", component: () => import("../components/logs/LogsWrapper.vue")},

    //Namespaces
    {name: "namespaces/list", path: "/:tenant?/namespaces", component: () => import("override/components/namespaces/Namespaces.vue")},
    {name: "namespaces/create", path: "/:tenant?/namespaces/new/:tab?", component: () => import("../components/namespaces/Namespace.vue")},
    {name: "namespaces/update", path: "/:tenant?/namespaces/edit/:id/:tab?", component: () => import("../components/namespaces/Namespace.vue")},

    //Docs
    {name: "docs/view", path: "/:tenant?/docs/:path(.*)?", component: () => import("../components/docs/Docs.vue"), meta: {layout: OnlyLeftMenuLayout}},

    //Settings
    {name: "settings", path: "/:tenant?/settings", component: () => import("override/components/settings/Settings.vue")},

    //Admin
    {name: "admin/triggers", path: "/:tenant?/admin/triggers", component: () => import("../components/admin/Triggers.vue")},
    {name: "admin/stats", path: "/:tenant?/admin/stats", component: () => import("override/components/admin/stats/Stats.vue")},

    //Setup
    {name: "setup", path: "/:tenant?/setup", component: () => import("../components/basicauth/BasicAuthSetup.vue"), meta: {layout: FullScreenLayout}},
    //Login
    {name: "login", path: "/:tenant?/login", component: () => import("../components/basicauth/BasicAuthLogin.vue"), meta: {layout: FullScreenLayout}},

    //Errors
    {name: "errors/404-wildcard", path: "/:pathMatch(.*)", component: Errors, props: {code: 404}},

    //Demo Pages
    {name: "apps/list", path: "/:tenant?/apps", component: DemoApps},
    {name: "tests/list", path: "/:tenant?/tests", component: DemoTests},
    {name: "admin/iam", path: "/:tenant?/admin/iam", component: DemoIAM},
    {name: "admin/tenants/list", path: "/:tenant?/admin/tenants", component: DemoTenants},
    {name: "admin/auditlogs/list", path: "/:tenant?/admin/auditlogs", component: DemoAuditLogs},
    {name: "admin/instance", path: "/:tenant?/admin/instance", component: DemoInstance},
];
