import ai from "./ai"
import auth from "./auth"
import execution from "./executions"
import flow from "./flow"
import graph from "./graph"
import namespace from "./namespaces"
import template from "./template"
import editor from "./editor";
import service from "./service"

export default {
    modules: {
        ai,
        flow,
        template,
        execution,
        namespace,
        auth,
        graph,
        editor,
        service,
    }
}
