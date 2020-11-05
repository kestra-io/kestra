import auth from "./auth"
import core from "./core"
import execution from "./executions"
import flow from "./flow"
import graph from "./graph"
import layout from "./layout"
import log from "./logs"
import namespace from "./namespaces"
import plugin from "./plugins"
import settings from "./settings"
import stat from "./stat"
import template from "./template"
import taskrun from "./taskruns"

export default {
    modules: {
        core,
        settings,
        flow,
        template,
        execution,
        log,
        stat,
        namespace,
        layout,
        auth,
        graph,
        plugin,
        taskrun
    }
}
