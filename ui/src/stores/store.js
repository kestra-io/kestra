import api from "./api"
import auth from "./auth"
import core from "./core"
import execution from "./executions"
import flow from "./flow"
import graph from "./graph"
import layout from "./layout"
import log from "./logs"
import namespace from "./namespaces"
import misc from "./miscs"
import plugin from "./plugins"
import stat from "./stat"
import template from "./template"
import taskrun from "./taskruns"
import trigger from "./trigger";
import editor from "./editor";
import doc from "./doc";
import bookmarks from "./bookmarks";
import dashboard from "./dashboard";
import code from "./code";
import blueprints from "./blueprints";

export default {
    modules: {
        api,
        core,
        flow,
        template,
        execution,
        log,
        stat,
        namespace,
        misc,
        layout,
        auth,
        graph,
        plugin,
        taskrun,
        trigger,
        editor,
        doc,
        bookmarks,
        dashboard,
        code,
        blueprints,
    }
}
