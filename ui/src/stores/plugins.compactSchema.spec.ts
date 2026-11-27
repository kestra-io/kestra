import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

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

describe("plugins store compact schema", () => {
    beforeEach(() => {
        vi.resetModules()
        setActivePinia(createPinia())
    })

    // The no-code editors read `items.anyOf` and a property's own keys directly: a `$ref` left in place renders an empty field.
    it("hands the no-code editors the schema with the backend's shared definitions inlined back", async () => {
        const subtypes = [{$ref: "#/definitions/a.Log"}]
        schemasFromTypeMock.mockResolvedValue({
            $ref: "#/definitions/a.Flow",
            definitions: {
                "a.Flow": {properties: {tasks: {type: "array", items: {$ref: "#/definitions/shared-subtypes"}}}},
                "a.Log": {properties: {retry: {$ref: "#/definitions/shared-property-retry"}}},
                "shared-subtypes": {anyOf: subtypes},
                "shared-property-retry": {type: "object", $group: "advanced"},
            },
        })
        const {usePluginsStore} = await import("./plugins")
        const store = usePluginsStore()

        await store.loadSchemaType({type: "flow"})

        expect(store.flowRootProperties?.tasks).toEqual({type: "array", items: {anyOf: subtypes}})
        expect(store.flowDefinitions["a.Log"]).toEqual({properties: {retry: {type: "object", $group: "advanced"}}})
    })
})
