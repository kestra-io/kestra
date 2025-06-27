import auth from "./auth"
import core from "./core"
import execution from "./executions"
import flow from "./flow"
import graph from "./graph"
import namespace from "./namespaces"
import misc from "./miscs"
import template from "./template"
import taskrun from "./taskruns"
import trigger from "./trigger";
import editor from "./editor";
import doc from "./doc";
import dashboard from "./dashboard";
import service from "./service"

export default {
    modules: {
        core,
        flow,
        template,
        execution,
        namespace,
        misc,
        auth,
        graph,
        taskrun,
        trigger,
        editor,
        doc,
        dashboard,
        service,
    }
}
