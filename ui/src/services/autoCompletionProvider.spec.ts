import {beforeEach, describe, expect, it, vi} from "vitest"
import {
    PebbleAutoCompletion,
    YamlAutoCompletion,
    fillExpressionCache,
    functionToSnippet,
    resetExpressionCache,
} from "./autoCompletionProvider"

const {axiosGet} = vi.hoisted(() => ({axiosGet: vi.fn()}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({
        get: axiosGet,
    }),
}))

vi.mock("../override/utils/route", () => ({
    apiUrlWithoutTenants: () => "http://localhost/api/v1",
}))

const FILTERS_URL = "http://localhost/api/v1/pebble/filters"
const FUNCTIONS_URL = "http://localhost/api/v1/pebble/functions"

const secretFunction = {
    name: "secret",
    arguments: [
        {name: "key", defaultValue: "'MY_SECRET'"},
        {name: "namespace", defaultValue: "flow.namespace"},
        {name: "subkey", defaultValue: null},
    ],
    deprecated: false,
    replacement: null,
}

describe("autoCompletionProvider", () => {
    beforeEach(() => {
        axiosGet.mockReset()
        resetExpressionCache()
    })

    describe("functionToSnippet", () => {
        it("numbers placeholders from the arguments that are actually inserted", () => {
            expect(functionToSnippet({
                name: "render",
                arguments: [
                    {name: "template", defaultValue: null},
                    {name: "lower", defaultValue: "0"},
                    {name: "blank", defaultValue: ""},
                    {name: "upper", defaultValue: "10"},
                ],
            })).toBe("render(lower=${1:0}, blank=${2:}, upper=${3:10})")
        })
    })

    describe("expression cache", () => {
        it("returns a filled cache in the shape the editor reads, without calling the API", async () => {
            const filters = ["abs", "capitalize"]
            fillExpressionCache(filters, [secretFunction])

            const provider = new PebbleAutoCompletion()
            expect(await provider.filterAutoCompletion()).toEqual(filters)
            expect(await provider.functionsWithDefaults()).toEqual([secretFunction])
            expect(axiosGet).not.toHaveBeenCalled()
        })

        it("reuses one successful response across provider instances until the cache is reset", async () => {
            axiosGet.mockImplementation((url: string) => {
                if (url === FILTERS_URL) {
                    return Promise.resolve({data: ["jq", "yaml"]})
                }
                return Promise.resolve({data: [{name: "uuid", arguments: []}]})
            })

            expect(await new PebbleAutoCompletion().filterAutoCompletion()).toEqual(["jq", "yaml"])
            expect(await new YamlAutoCompletion().filterAutoCompletion()).toEqual(["jq", "yaml"])
            expect(axiosGet).toHaveBeenCalledTimes(1)
            expect(axiosGet).toHaveBeenCalledWith(FILTERS_URL)

            resetExpressionCache()
            expect(await new PebbleAutoCompletion().functionsWithDefaults()).toEqual([{name: "uuid", arguments: []}])
            expect(axiosGet).toHaveBeenCalledTimes(2)
            expect(axiosGet).toHaveBeenLastCalledWith(FUNCTIONS_URL)
        })

        it("does not cache a failed request, then returns the next successful list", async () => {
            axiosGet.mockRejectedValueOnce(new Error("Network error"))
            const provider = new PebbleAutoCompletion()

            expect(await provider.filterAutoCompletion()).toEqual([])
            expect(await provider.functionsWithDefaults()).toEqual([])
            expect(axiosGet).toHaveBeenCalledTimes(2)

            axiosGet.mockResolvedValueOnce({data: ["capitalize"]})
            expect(await provider.filterAutoCompletion()).toEqual(["capitalize"])
            expect(axiosGet).toHaveBeenCalledTimes(3)
            expect(axiosGet).toHaveBeenNthCalledWith(1, FILTERS_URL)
            expect(axiosGet).toHaveBeenNthCalledWith(2, FUNCTIONS_URL)
            expect(axiosGet).toHaveBeenNthCalledWith(3, FILTERS_URL)
        })

        it("caches an empty success so a later read does not refetch or throw", async () => {
            axiosGet.mockResolvedValue({data: []})
            const provider = new YamlAutoCompletion()

            expect(await provider.filterAutoCompletion()).toEqual([])
            expect(await provider.functionsWithDefaults()).toEqual([])
            expect(await provider.filterAutoCompletion()).toEqual([])
            expect(await provider.functionsWithDefaults()).toEqual([])
            expect(axiosGet).toHaveBeenCalledTimes(2)
            expect(axiosGet).toHaveBeenNthCalledWith(1, FILTERS_URL)
            expect(axiosGet).toHaveBeenNthCalledWith(2, FUNCTIONS_URL)
        })

        it("keeps the filter cache and the function cache independent", async () => {
            axiosGet.mockImplementation((url: string) => {
                if (url === FILTERS_URL) {
                    return Promise.resolve({data: ["abs"]})
                }
                return Promise.resolve({data: [{name: "uuid", arguments: []}]})
            })

            const provider = new PebbleAutoCompletion()
            expect(await provider.filterAutoCompletion()).toEqual(["abs"])
            axiosGet.mockClear()

            expect(await provider.functionsWithDefaults()).toEqual([{name: "uuid", arguments: []}])
            expect(axiosGet).toHaveBeenCalledTimes(1)
            expect(axiosGet).toHaveBeenCalledWith(FUNCTIONS_URL)
        })
    })

    describe("no duplicate suggestions", () => {
        it("replaces the cache when it is filled again instead of appending to it", async () => {
            fillExpressionCache(["abs"], [secretFunction])
            fillExpressionCache(["abs", "upper"], [secretFunction])
            const provider = new PebbleAutoCompletion()

            expect(await provider.filterAutoCompletion()).toEqual(["abs", "upper"])
            expect(await provider.functionsWithDefaults()).toEqual([secretFunction])
        })
    })

    describe("suggestions for an empty or unknown input", () => {
        it("resolves to no suggestions instead of throwing", async () => {
            const provider = new PebbleAutoCompletion()

            expect(await provider.rootFieldAutoCompletion()).toEqual([])
            expect(await provider.rootFieldAutoCompletion({source: "", offset: 0})).toEqual([])
            expect(await provider.nestedFieldAutoCompletion("", undefined, "")).toEqual([])
            expect(await provider.nestedFieldAutoCompletion("id: flow", {}, "unknown")).toEqual([])
            expect(await provider.functionAutoCompletion(undefined, "", {})).toEqual([])
            expect(await provider.functionAutoCompletion({}, "secret", {namespace: "'company.team'"})).toEqual([])
            expect(axiosGet).not.toHaveBeenCalled()
        })
    })
})
