import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"
import type {usePluginsStore} from "./plugins"

const schemasFromTypeMock = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk/plugins", () => ({
    schemasFromType: schemasFromTypeMock,
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1",
    apiUrlWithoutTenants: () => "/api/v1",
    baseUrl: "/",
}))

vi.mock("../utils/tabTracking", () => ({
    trackPluginDocumentationView: vi.fn(),
}))

const FULL_SCHEMA = {$ref: "#/definitions/Flow", definitions: {Flow: {type: "object", properties: {id: {type: "string"}}}}}

describe("plugins store schema load", () => {
    let store: ReturnType<typeof usePluginsStore>
    let respond: (schema: unknown) => void
    let fail: (error: Error) => void

    beforeEach(async () => {
        vi.resetModules()
        schemasFromTypeMock.mockReset()
        schemasFromTypeMock.mockImplementation(() => new Promise((resolve, reject) => {
            respond = resolve
            fail = reject
        }))
        setActivePinia(createPinia())
        const {usePluginsStore} = await import("./plugins")
        store = usePluginsStore()
    })

    it("shares one request between panels asking for the same schema at the same time", async () => {
        const first = store.lazyLoadSchemaType({type: "flow"})
        const second = store.lazyLoadSchemaType({type: "flow"})
        respond(FULL_SCHEMA)

        expect(await first).toBe(await second)
        expect(schemasFromTypeMock).toHaveBeenCalledTimes(1)
    })

    it("serves the placeholder schema while the full one loads, then switches to it", async () => {
        const placeholder = store.flowSchema
        const load = store.lazyLoadSchemaType({type: "flow"})

        expect(store.isFlowSchemaLoading).toBe(true)
        expect(store.flowRootProperties).toHaveProperty("namespace")

        respond(FULL_SCHEMA)
        await load

        expect(store.isFlowSchemaLoading).toBe(false)
        expect(store.flowSchema).not.toBe(placeholder)
        expect(Object.keys(store.flowRootProperties ?? {})).toEqual(["id"])
    })

    it("stops reporting a load and lets the next panel retry when the request fails", async () => {
        const load = store.lazyLoadSchemaType({type: "flow"})
        fail(new Error("network"))
        await expect(load).rejects.toThrow("network")

        expect(store.isFlowSchemaLoading).toBe(false)
        void store.lazyLoadSchemaType({type: "flow"})
        expect(schemasFromTypeMock).toHaveBeenCalledTimes(2)
    })

    // Topology also mounts read-only on an execution, where nothing ever needs the megabytes of schema.
    it("fetches the flow schema only once a form calls the loader, and reports it on its way", () => {
        expect(schemasFromTypeMock).not.toHaveBeenCalled()

        store.flowSchemaLoader.load()

        expect(schemasFromTypeMock).toHaveBeenCalledTimes(1)
        expect(store.flowSchemaLoader.isLoading.value).toBe(true)
    })
})
