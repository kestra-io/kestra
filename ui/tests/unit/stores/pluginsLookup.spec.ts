import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn()}),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1",
    apiUrlWithoutTenants: () => "/api/v1",
    baseUrl: "/",
}))

vi.mock("../../../src/utils/tabTracking", () => ({
    trackPluginDocumentationView: vi.fn(),
}))

const idOf = (p: any) => `${p?.name ?? ""}#${p?.subGroup ?? ""}`

const PARENT = {name: "gcp", group: "io.kestra.plugin.gcp", title: "GCP"}
const SUBGROUP_BQ = {
    name: "gcp",
    group: "io.kestra.plugin.gcp",
    subGroup: "io.kestra.plugin.gcp.bigquery",
    title: "BigQuery",
    tasks: [
        {cls: "io.kestra.plugin.gcp.bigquery.Query", deprecated: false},
        {cls: "io.kestra.plugin.gcp.bigquery.Load", deprecated: false},
    ],
}
const SUBGROUP_GCS = {
    name: "gcp",
    group: "io.kestra.plugin.gcp",
    subGroup: "io.kestra.plugin.gcp.gcs",
    title: "GCS",
    tasks: [{cls: "io.kestra.plugin.gcp.gcs.Upload", deprecated: false}],
}
const STANDALONE = {
    name: "subflow",
    group: "io.kestra.plugin.core",
    title: "Core",
    tasks: [{cls: "io.kestra.plugin.core.flow.Subflow", deprecated: false}],
}

describe("plugins store lookups", () => {
    let store: any

    beforeEach(async () => {
        setActivePinia(createPinia())
        const {usePluginsStore} = await import("../../../src/stores/plugins")
        store = usePluginsStore()
        store.plugins = [PARENT, SUBGROUP_BQ, SUBGROUP_GCS, STANDALONE]
    })

    describe("findPluginByCls", () => {
        it("returns the subgroup matching the cls prefix", () => {
            expect(idOf(store.findPluginByCls("io.kestra.plugin.gcp.bigquery.Query"))).toBe(idOf(SUBGROUP_BQ))
        })

        it("prefers a subgroup match over a parent that contains the cls", () => {
            expect(idOf(store.findPluginByCls("io.kestra.plugin.gcp.bigquery.Load"))).toBe(idOf(SUBGROUP_BQ))
        })

        it("falls back to scanning element entries when no subgroup matches", () => {
            expect(idOf(store.findPluginByCls("io.kestra.plugin.core.flow.Subflow"))).toBe(idOf(STANDALONE))
        })

        it("returns null when cls is unknown", () => {
            expect(store.findPluginByCls("does.not.exist.Task")).toBeNull()
        })

        it("returns null on null/undefined input", () => {
            expect(store.findPluginByCls(null)).toBeNull()
            expect(store.findPluginByCls(undefined)).toBeNull()
        })
    })

    describe("findPluginByName", () => {
        it("returns the parent group when no subGroup is provided", () => {
            expect(idOf(store.findPluginByName("gcp"))).toBe(idOf(PARENT))
        })

        it("returns the matching subgroup when subGroup is provided", () => {
            expect(idOf(store.findPluginByName("gcp", "io.kestra.plugin.gcp.bigquery"))).toBe(idOf(SUBGROUP_BQ))
        })

        it("returns null when name is missing", () => {
            expect(store.findPluginByName(null)).toBeNull()
            expect(store.findPluginByName(undefined)).toBeNull()
        })

        it("returns null when no plugin matches", () => {
            expect(store.findPluginByName("nope")).toBeNull()
            expect(store.findPluginByName("gcp", "nope")).toBeNull()
        })
    })

    describe("sidebarPluginsFor", () => {
        it("returns subgroups when there is more than one under the owning group (by cls)", () => {
            const out = store.sidebarPluginsFor({cls: "io.kestra.plugin.gcp.bigquery.Query"}).map(idOf)
            expect(out).toContain(idOf(SUBGROUP_BQ))
            expect(out).toContain(idOf(SUBGROUP_GCS))
            expect(out).not.toContain(idOf(PARENT))
        })

        it("returns subgroups when there is more than one under the owning group (by owner)", () => {
            const out = store.sidebarPluginsFor({owner: PARENT}).map(idOf)
            expect(out).toContain(idOf(SUBGROUP_BQ))
            expect(out).toContain(idOf(SUBGROUP_GCS))
        })

        it("returns top-level peers when no cls/owner resolves to a group", () => {
            const out = store.sidebarPluginsFor({}).map(idOf)
            expect(out).toContain(idOf(PARENT))
            expect(out).toContain(idOf(STANDALONE))
            expect(out).not.toContain(idOf(SUBGROUP_BQ))
        })

        it("returns the single top-level entry when the group has no >1 subgroups", () => {
            store.plugins = [STANDALONE]
            const out = store.sidebarPluginsFor({cls: "io.kestra.plugin.core.flow.Subflow"}).map(idOf)
            expect(out).toEqual([idOf(STANDALONE)])
        })
    })
})
