import type {RouteLocationNormalized} from "vue-router"
import {useDocStore} from "../stores/doc"
import {usePluginsStore} from "../stores/plugins"

function section(route: RouteLocationNormalized): string {
    return String(route.name ?? "").split("/")[0]
}

export function documentationGuard(to: RouteLocationNormalized, from: RouteLocationNormalized) {
    const pathArray = to.path.split("/")
    useDocStore().docId = pathArray[pathArray.length - 1]

    // Every editor shares one plugin documentation, so a dashboard's KPI doc would otherwise open in the flow editor (kestra-io/kestra#18469).
    if (section(to) !== section(from)) {
        usePluginsStore().updateDocumentation()
    }

    if (to.query["showDocId"] === undefined && from.query["showDocId"] !== undefined) {
        return {path: to.path, query: {...to.query, showDocId: from.query["showDocId"]}}
    }
}
