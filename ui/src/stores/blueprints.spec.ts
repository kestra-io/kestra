import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

const {get, post} = vi.hoisted(() => ({get: vi.fn(), post: vi.fn()}))

vi.mock("@kestra-io/kestra-sdk", () => ({useClient: () => ({get, post})}))
vi.mock("override/utils/route", () => ({apiUrl: () => "/api/v1/main"}))
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({configs: {edition: "OSS", version: "1.3.0"}})}))
vi.mock("../utils/tabTracking", () => ({trackBlueprintSelection: vi.fn()}))

import {useBlueprintsStore} from "./blueprints"

describe("blueprints store", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        get.mockReset()
        post.mockReset()
    })

    /** The custom blueprints API filters through `filters[...]` keys, not plain `q` and `tags` params. */
    it("turns the custom blueprint search and tags into API filters", async () => {
        get.mockResolvedValue({data: {results: [], total: 0}})

        await useBlueprintsStore().getBlueprints({type: "custom", params: {q: "etl", tags: ["daily", "s3"], page: 2}})

        expect(get).toHaveBeenCalledWith("/api/v1/main/blueprints/custom", {
            params: {"page": 2, "filters[q][EQUALS]": "etl", "filters[tags][IN]": "daily,s3"},
        })
    })

    /** A user without access to custom blueprints sees an empty list, while real errors still surface. */
    it("shows no custom blueprints to a caller the server does not authenticate, and rethrows anything else", async () => {
        get.mockRejectedValueOnce({status: 401})
        await expect(useBlueprintsStore().getBlueprints({type: "custom"})).resolves.toEqual({results: [], total: 0})

        get.mockRejectedValueOnce({status: 500})
        await expect(useBlueprintsStore().getBlueprints({type: "custom"})).rejects.toEqual({status: 500})
    })

    /** OSS asks the community catalog for its own version, without the Enterprise blueprints. */
    it("asks for the OSS community blueprints of this version only", async () => {
        get.mockResolvedValue({data: {results: []}})

        await useBlueprintsStore().getBlueprints({type: "community", kind: "flow"})

        expect(get.mock.calls[0][0]).toBe("https://api.kestra.io/v1/blueprints/kinds/flow/versions/1.3.0?ee=false")
    })

    /** The flow blueprint editor shows these lines as its validation errors. */
    it("lists the validation errors of a flow blueprint source, and none once it is valid", async () => {
        const store = useBlueprintsStore()

        post.mockResolvedValueOnce({data: {errors: [{path: "tasks[log].message", detail: "must not be null"}]}})
        await store.validateFlowBlueprint("id: broken")
        expect(store.validationErrors).toEqual(["tasks[log].message: must not be null"])

        post.mockResolvedValueOnce({data: {errors: []}})
        await store.validateFlowBlueprint("id: fixed")
        expect(store.validationErrors).toBeUndefined()
    })
})
