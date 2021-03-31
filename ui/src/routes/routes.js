import ExecutionRoot from "../components/executions/ExecutionRoot.vue"
import Executions from "../components/executions/Executions.vue"
import TaskRuns from "../components/taskruns/TaskRuns.vue"
import FlowEdit from "../components/flows/FlowEdit.vue"
import FlowRoot from "../components/flows/FlowRoot.vue"
import Flows from "../components/flows/Flows.vue"
import LogsWrapper from "../components/logs/LogsWrapper.vue"
import Plugin from "../components/plugins/Plugin.vue"
import Settings from "../components/settings/Settings.vue"
import TemplateEdit from "../components/templates/TemplateEdit.vue"
import Templates from "../components/templates/Templates.vue"

export default {
    mode: "history",
    // eslint-disable-next-line no-undef
    base: KESTRA_UI_PATH,
    routes: [
        //Flows
        {name: "home", path: "/", component: Flows},
        {name: "flows/list", path: "/flows", component: Flows},
        {name: "flows/create", path: "/flows/new", component: FlowEdit},
        {name: "flows/update", path: "/flows/edit/:namespace/:id", component: FlowRoot},

        //Executions
        {name: "executions/list", path: "/executions", component: Executions},
        {name: "executions/update", path: "/executions/:namespace/:flowId/:id", component: ExecutionRoot},

        //TaskRuns
        {name: "taskruns/list", path: "/taskruns", component: TaskRuns},

        //Documentation
        {name: "plugins/list", path: "/plugins", component: Plugin},
        {name: "plugins/view", path: "/plugins/:cls", component: Plugin},

        //Templates
        {name: "templates/list", path: "/templates", component: Templates},
        {name: "templates/create", path: "/templates/new", component: TemplateEdit},
        {name: "templates/update", path: "/templates/edit/:namespace/:id", component: TemplateEdit},

        //Settings
        {name: "logs/list", path: "/logs", component: LogsWrapper},

        //Settings
        {name: "settings", path: "/settings", component: Settings},
    ]
}
