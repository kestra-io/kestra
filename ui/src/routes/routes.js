import ExecutionRoot from "../components/executions/ExecutionRoot.vue"
import Executions from "../components/executions/Executions.vue"
import TaskRuns from "../components/taskruns/TaskRuns.vue"
import FlowRoot from "../components/flows/FlowRoot.vue"
import Flows from "../components/flows/Flows.vue"
import LogsWrapper from "../components/logs/LogsWrapper.vue"
import Plugin from "../components/plugins/Plugin.vue"
import Settings from "../components/settings/Settings.vue"
import TemplateEdit from "../components/templates/TemplateEdit.vue"
import Templates from "../components/templates/Templates.vue"
import FlowsSearch from "../components/flows/FlowsSearch.vue";
import Errors from "../components/errors/Errors.vue";
import Home from "../components/home/Home.vue";
import Welcome from "../components/onboarding/Welcome.vue";
import FlowCreate from "../components/flows/FlowCreate.vue";
import FlowMetrics from "../components/flows/FlowMetrics.vue";
import Blueprints from "override/components/flows/blueprints/Blueprints.vue";
import BlueprintDetail from "../components/flows/blueprints/BlueprintDetail.vue";

export default [
    //Flows
    {name: "home", path: "/", component: Home},
    {name: "welcome", path: "/welcome", component: Welcome},
    {name: "flows/list", path: "/flows", component: Flows},
    {name: "flows/search", path: "/flows/search", component: FlowsSearch},
    {name: "flows/create", path: "/flows/new", component: FlowCreate},
    {name: "flows/update", path: "/flows/edit/:namespace/:id/:tab?", component: FlowRoot},
    {name: "flows/metrics", path: "/flows/metrics", component: FlowMetrics},

    //Executions
    {name: "executions/list", path: "/executions", component: Executions},
    {name: "executions/update", path: "/executions/:namespace/:flowId/:id/:tab?", component: ExecutionRoot},

    //TaskRuns
    {name: "taskruns/list", path: "/taskruns", component: TaskRuns},

    //Blueprints
    {name: "blueprints", path: "/blueprints", component: Blueprints, props: {topNavbar: false}},
    {name: "blueprints/view", path: "/blueprints/:blueprintId", component: BlueprintDetail, props: true},

    //Documentation
    {name: "plugins/list", path: "/plugins", component: Plugin},
    {name: "plugins/view", path: "/plugins/:cls", component: Plugin},

    //Templates
    {name: "templates/list", path: "/templates", component: Templates},
    {name: "templates/create", path: "/templates/new", component: TemplateEdit},
    {name: "templates/update", path: "/templates/edit/:namespace/:id", component: TemplateEdit},

    //Logs
    {name: "logs/list", path: "/logs", component: LogsWrapper},

    //Settings
    {name: "settings", path: "/settings", component: Settings},

    //Errors
    {name: "errors/404-wildcard", path: "/:pathMatch(.*)", component: Errors, props: {code: 404}},
]