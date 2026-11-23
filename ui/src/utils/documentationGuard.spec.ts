import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"
import {NavigationFailureType, type NavigationFailure, type RouteLocationNormalized, type RouteMeta} from "vue-router"

import type {Plugin} from "./pluginUtils"
import type {usePluginsStore} from "../stores/plugins"
import type {useDocStore} from "../stores/doc"
import type {documentationGuard as DocumentationGuard, updateDocId as UpdateDocId} from "./documentationGuard"

const pluginDocumentationMock = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk/plugins", () => ({
    pluginDocumentation: pluginDocumentationMock,
    pluginDocumentationFromVersion: vi.fn(),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1",
    apiUrlWithoutTenants: () => "/api/v1",
    baseUrl: "/",
}))

vi.mock("../stores/api", () => ({
    API_URL: "https://api.kestra.io",
}))

vi.mock("./tabTracking", () => ({
    trackPluginDocumentationView: vi.fn(),
}))

const KPI = "io.kestra.plugin.core.dashboard.chart.KPI"

const CORE: Plugin = {
    name: "core",
    title: "Core",
    group: "io.kestra.plugin.core",
    tasks: [],
    charts: [{cls: KPI, deprecated: false}],
}

function route(name: string, path: string, meta: RouteMeta = {}): RouteLocationNormalized {
    return {name, path, meta, query: {}, params: {}} as unknown as RouteLocationNormalized
}

const DASHBOARD = route("dashboards/create", "/dashboards/new")
const FLOW_CREATE = route("flows/create", "/flows/new")
const FLOW_UPDATE = route("flows/update", "/flows/edit/io.kestra/hello")

describe("documentationGuard", () => {
    let store: ReturnType<typeof usePluginsStore>
    let documentationGuard: typeof DocumentationGuard

    beforeEach(async () => {
        vi.resetModules()
        pluginDocumentationMock.mockReset()
        pluginDocumentationMock.mockResolvedValue({schema: {properties: {properties: {}}}})
        setActivePinia(createPinia())
        const {usePluginsStore} = await import("../stores/plugins")
        ;({documentationGuard} = await import("./documentationGuard"))
        store = usePluginsStore()
        store.plugins = [CORE]
    })

    it("closes the plugin documentation when leaving for another editor", async () => {
        await store.updateDocumentation({cls: KPI})

        documentationGuard(FLOW_CREATE, DASHBOARD)

        expect(store.editorPlugin).toBeUndefined()
    })

    it("keeps the plugin documentation within the same editor", async () => {
        await store.updateDocumentation({cls: KPI})

        documentationGuard(FLOW_UPDATE, FLOW_CREATE)

        expect(store.editorPlugin?.cls).toBe(KPI)
    })

    it("drops a documentation still loading when leaving for another editor", async () => {
        const loading = store.updateDocumentation({cls: KPI})

        documentationGuard(FLOW_CREATE, DASHBOARD)
        await loading

        expect(store.editorPlugin).toBeUndefined()
    })
})

describe("updateDocId", () => {
    let docStore: ReturnType<typeof useDocStore>
    let updateDocId: typeof UpdateDocId

    beforeEach(async () => {
        vi.resetModules()
        setActivePinia(createPinia())
        ;({updateDocId} = await import("./documentationGuard"))
        docStore = (await import("../stores/doc")).useDocStore()
    })

    it.each([
        ["flows/update/overview", "flows"],
        ["admin/auditlogs/list", "auditlogs"],
        ["admin/instance/worker-groups", "worker-groups"],
        ["admin/instance", "instance"],
    ])("opens the docs of the feature route %s belongs to", (name, docId) => {
        updateDocId(route(name, "/"), DASHBOARD)

        expect(docStore.docId).toBe(docId)
    })

    it("opens the docs page a route declares over its feature's", () => {
        updateDocId(route("preferences", "/preferences", {docId: "settings"}), DASHBOARD)

        expect(docStore.docId).toBe("settings")
    })

    it("lets a route pick its docs page from the location, falling back to its feature", () => {
        const instance = {...route("admin/instance", "/admin/instance/users", {docId: (to) => to.params.tab === "users" ? "users" : undefined}), params: {tab: "users"}}

        updateDocId(instance, DASHBOARD)
        expect(docStore.docId).toBe("users")

        updateDocId({...instance, params: {tab: "auditlogs"}}, DASHBOARD)
        expect(docStore.docId).toBe("instance")
    })

    it("keeps the reader's place in the docs within a feature and starts over in another", () => {
        updateDocId(route("flows/list", "/flows"), DASHBOARD)
        docStore.docPath = "docs/workflow-components/subflows"

        updateDocId(FLOW_UPDATE, route("flows/list", "/flows"))
        expect(docStore.docPath).toBe("docs/workflow-components/subflows")

        updateDocId(route("kv/list", "/kv"), FLOW_UPDATE)
        expect(docStore.docId).toBe("kv")
        expect(docStore.docPath).toBeUndefined()
    })

    it("leaves the docs on the current page when the navigation was cancelled", () => {
        updateDocId(FLOW_UPDATE, DASHBOARD)
        docStore.docPath = "docs/workflow-components/flow"

        updateDocId(route("kv/list", "/kv"), FLOW_UPDATE, {type: NavigationFailureType.aborted} as unknown as NavigationFailure)

        expect(docStore.docId).toBe("flows")
        expect(docStore.docPath).toBe("docs/workflow-components/flow")
    })
})
