import type {NavigationFailure, RouteLocationNormalized} from "vue-router"
import {useDocStore} from "../stores/doc"
import {usePluginsStore} from "../stores/plugins"

declare module "vue-router" {
    interface RouteMeta {
        /** Front-matter `docId` of the page the Docs panel opens on; defaults to the route's feature, the first segment of its name after any `admin/` or `admin/instance/` prefix. */
        docId?: string | ((to: RouteLocationNormalized) => string | undefined)
    }
}

function section(route: RouteLocationNormalized): string {
    return String(route.name ?? "").split("/")[0]
}

export function documentationGuard(to: RouteLocationNormalized, from: RouteLocationNormalized) {
    // Every editor shares one plugin documentation, so a dashboard's KPI doc would otherwise open in the flow editor (kestra-io/kestra#18469).
    if (section(to) !== section(from)) {
        usePluginsStore().updateDocumentation()
    }

    if (to.query["showDocId"] === undefined && from.query["showDocId"] !== undefined) {
        return {path: to.path, query: {...to.query, showDocId: from.query["showDocId"]}}
    }
}

/** Points the Docs panel at the page of the feature just navigated to; a navigation the user cancelled leaves it alone. */
export function updateDocId(to: RouteLocationNormalized, _from: RouteLocationNormalized, failure?: NavigationFailure | void) {
    if (failure) return

    const declared = typeof to.meta.docId === "function" ? to.meta.docId(to) : to.meta.docId
    const docId = declared ?? String(to.name ?? "").replace(/^admin\/(instance\/)?/, "").split("/")[0]
    const docStore = useDocStore()
    if (docStore.docId !== docId) {
        docStore.docId = docId
        docStore.docPath = undefined
    }
}
