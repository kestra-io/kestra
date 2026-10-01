import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"
import type {RouteLocationNormalized} from "vue-router"

import type {Plugin} from "../../../src/utils/pluginUtils"
import type {usePluginsStore} from "../../../src/stores/plugins"
import type {documentationGuard as DocumentationGuard} from "../../../src/utils/documentationGuard"

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

vi.mock("../../../src/stores/api", () => ({
    API_URL: "https://api.kestra.io",
}))

vi.mock("../../../src/utils/tabTracking", () => ({
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

function route(name: string, path: string): RouteLocationNormalized {
    return {name, path, query: {}, params: {}} as unknown as RouteLocationNormalized
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
        const {usePluginsStore} = await import("../../../src/stores/plugins")
        ;({documentationGuard} = await import("../../../src/utils/documentationGuard"))
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
