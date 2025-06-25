import auth from "./auth"
import core from "./core"
import execution from "./executions"
import flow from "./flow"
import graph from "./graph"
import layout from "./layout"
import namespace from "./namespaces"
import misc from "./miscs"
import stat from "./stat"
import template from "./template"
import taskrun from "./taskruns"
import trigger from "./trigger";
import editor from "./editor";
import doc from "./doc";
import bookmarks from "./bookmarks";
import dashboard from "./dashboard";
import service from "./service"

export default {
    modules: {
        core,
        flow,
        template,
        execution,
        stat,
        namespace,
        misc,
        layout,
        auth,
        graph,
        taskrun,
        trigger,
        editor,
        doc,
        bookmarks,
        dashboard,
        service,
    }
}
