import ExecutionRoot from "../components/executions/ExecutionRoot.vue"
import Executions from "../components/executions/Executions.vue"
import TaskRuns from "../components/taskruns/TaskRuns.vue"
import FlowRoot from "../components/flows/FlowRoot.vue"
import Flows from "../components/flows/Flows.vue"
import LogsWrapper from "../components/logs/LogsWrapper.vue"
import Plugin from "../components/plugins/Plugin.vue"
import Settings from "override/components/settings/Settings.vue"
import TemplateEdit from "../components/templates/TemplateEdit.vue"
import Templates from "../components/templates/Templates.vue"
import FlowsSearch from "../components/flows/FlowsSearch.vue";
import Errors from "../components/errors/Errors.vue";
import Home from "../components/home/Home.vue";
import Welcome from "../components/onboarding/Welcome.vue";
import FlowCreate from "../components/flows/FlowCreate.vue";
import Blueprints from "override/components/flows/blueprints/Blueprints.vue";
import BlueprintDetail from "../components/flows/blueprints/BlueprintDetail.vue";
import Triggers from "../components/admin/Triggers.vue";
import Stats from "override/components/admin/stats/Stats.vue";
import Namespaces from "../components/namespace/Namespaces.vue";
import Namespace from "../components/namespace/Namespace.vue";

export default [
    //Flows
    {name: "root", path: "/", redirect: {name: "home"}},
    {name: "home", path: "/:tenant?/dashboard", component: Home},
    {name: "welcome", path: "/:tenant?/welcome", component: Welcome},

    //Flows
    {name: "flows/list", path: "/:tenant?/flows", component: Flows},
    {name: "flows/search", path: "/:tenant?/flows/search", component: FlowsSearch},
    {name: "flows/create", path: "/:tenant?/flows/new", component: FlowCreate},
    {name: "flows/update", path: "/:tenant?/flows/edit/:namespace/:id/:tab?", component: FlowRoot},

    //Executions
    {name: "executions/list", path: "/:tenant?/executions", component: Executions},
    {name: "executions/update", path: "/:tenant?/executions/:namespace/:flowId/:id/:tab?", component: ExecutionRoot},

    //TaskRuns
    {name: "taskruns/list", path: "/:tenant?/taskruns", component: TaskRuns},

    //Blueprints
    {name: "blueprints", path: "/:tenant?/blueprints", component: Blueprints, props: {topNavbar: false}},
    {name: "blueprints/view", path: "/:tenant?/blueprints/:blueprintId", component: BlueprintDetail, props: true},

    //Documentation
    {name: "plugins/list", path: "/:tenant?/plugins", component: Plugin},
    {name: "plugins/view", path: "/:tenant?/plugins/:cls",   component: Plugin},

    //Templates
    {name: "templates/list", path: "/:tenant?/templates", component: Templates},
    {name: "templates/create", path: "/:tenant?/templates/new", component: TemplateEdit},
    {name: "templates/update", path: "/:tenant?/templates/edit/:namespace/:id", component: TemplateEdit},

    //Logs
    {name: "logs/list", path: "/:tenant?/logs", component: LogsWrapper},

    //Namespaces
    {name: "namespaces", path: "/:tenant?/namespaces", component: Namespaces},
    {name: "namespaces/update", path: "/:tenant?/namespaces/edit/:id/:tab?", component: Namespace},

    //Settings
    {name: "settings", path: "/:tenant?/settings", component: Settings},

    //Admin
    {name: "admin/triggers", path: "/:tenant?/admin/triggers", component: Triggers},
    {name: "admin/stats", path: "/:tenant?/admin/stats", component: Stats},

    //Errors
    {name: "errors/404-wildcard", path: "/:pathMatch(.*)", component: Errors, props: {code: 404}},
]