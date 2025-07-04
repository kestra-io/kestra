import ai from "./ai"
import auth from "./auth"
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
import service from "./service"

export default {
    modules: {
        ai,
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
        service,
    }
}
