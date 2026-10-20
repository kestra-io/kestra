import {beforeEach, describe, expect, it, vi} from "vitest"
import {
    QUOTE,
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

    it("exposes the single quote used to wrap a string suggestion", () => {
        expect(QUOTE).toBe("'")
        expect(QUOTE).toHaveLength(1)
    })

    describe("functionToSnippet", () => {
        it("keeps a quoted default quoted and leaves an unquoted default unquoted", () => {
            expect(functionToSnippet(secretFunction)).toBe(
                "secret(key=${1:'MY_SECRET'}, namespace=${2:flow.namespace})",
            )
        })

        it("copies every quote of a default verbatim, whatever quote style it uses", () => {
            expect(functionToSnippet({
                name: "kv",
                arguments: [
                    {name: "key", defaultValue: `${QUOTE}my${QUOTE}${QUOTE}key${QUOTE}`},
                    {name: "namespace", defaultValue: "\"company.team\""},
                    {name: "errorOnMissing", defaultValue: "false"},
                ],
            })).toBe("kv(key=${1:'my''key'}, namespace=${2:\"company.team\"}, errorOnMissing=${3:false})")
        })

        it("inserts each argument once, in the order the API lists them", () => {
            const snippet = functionToSnippet(secretFunction)

            expect(snippet.match(/key=/g)).toHaveLength(1)
            expect(snippet.match(/namespace=/g)).toHaveLength(1)
            expect(snippet).not.toContain("subkey")
            expect(snippet.indexOf("key=")).toBeLessThan(snippet.indexOf("namespace="))
        })

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

        it("leaves deprecation metadata out of the snippet", () => {
            expect(functionToSnippet({
                name: "currentDate",
                arguments: [{name: "format", defaultValue: "yyyy-MM-dd"}],
                deprecated: true,
                replacement: "now",
            })).toBe("currentDate(format=${1:yyyy-MM-dd})")
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

        it("returns a repeated filter name as sent, without appending a second copy", async () => {
            axiosGet.mockResolvedValue({data: ["upper", "upper", "lower"]})
            const provider = new PebbleAutoCompletion()

            expect(await provider.filterAutoCompletion()).toEqual(["upper", "upper", "lower"])
            expect(await provider.filterAutoCompletion()).toEqual(["upper", "upper", "lower"])
            expect(axiosGet).toHaveBeenCalledTimes(1)
        })

        it("treats a cache filled with empty lists as filled, so it never calls the API", async () => {
            fillExpressionCache([], [])
            const provider = new YamlAutoCompletion()

            expect(await provider.filterAutoCompletion()).toEqual([])
            expect(await provider.functionsWithDefaults()).toEqual([])
            expect(axiosGet).not.toHaveBeenCalled()
        })

        it("does not throw when the cache is reset before it was ever filled", async () => {
            expect(() => {
                resetExpressionCache()
                resetExpressionCache()
            }).not.toThrow()

            axiosGet.mockResolvedValue({data: ["abs"]})
            expect(await new PebbleAutoCompletion().filterAutoCompletion()).toEqual(["abs"])
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
        it("does not add a second copy of a filter when it is read again or from another provider", async () => {
            axiosGet.mockResolvedValue({data: ["abs", "capitalize", "jq"]})

            const first = await new PebbleAutoCompletion().filterAutoCompletion()
            const again = await new PebbleAutoCompletion().filterAutoCompletion()
            const fromYaml = await new YamlAutoCompletion().filterAutoCompletion()

            for (const filters of [first, again, fromYaml]) {
                expect(filters).toEqual(["abs", "capitalize", "jq"])
                expect(new Set(filters).size).toBe(filters.length)
            }
        })

        it("does not add a second copy of a function when it is read again", async () => {
            axiosGet.mockResolvedValue({data: [secretFunction, {name: "uuid", arguments: []}]})
            const provider = new PebbleAutoCompletion()

            await provider.functionsWithDefaults()
            const names = (await provider.functionsWithDefaults()).map(fn => fn.name)

            expect(names).toEqual(["secret", "uuid"])
        })

        it("replaces the cache when it is filled again instead of appending to it", async () => {
            fillExpressionCache(["abs"], [secretFunction])
            fillExpressionCache(["abs", "upper"], [secretFunction])
            const provider = new PebbleAutoCompletion()

            expect(await provider.filterAutoCompletion()).toEqual(["abs", "upper"])
            expect(await provider.functionsWithDefaults()).toEqual([secretFunction])
        })

        it("returns the list once to each of two concurrent reads, without merging them", async () => {
            axiosGet.mockResolvedValue({data: ["abs", "upper"]})
            const provider = new PebbleAutoCompletion()

            const [first, second] = await Promise.all([provider.filterAutoCompletion(), provider.filterAutoCompletion()])

            expect(first).toEqual(["abs", "upper"])
            expect(second).toEqual(["abs", "upper"])
            expect(await provider.filterAutoCompletion()).toEqual(["abs", "upper"])
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

        it("resolves a nested path to an empty completion list", async () => {
            const provider = new PebbleAutoCompletion()
            const source = "id: flow\nnamespace: company.team\n"

            expect(await provider.nestedFieldAutoCompletion(source, {}, "outputs.task1", source.length)).toEqual([])
            expect(await provider.nestedFieldAutoCompletion(source, {inputs: []}, "inputs.environment.region", 0)).toEqual([])
            expect(await provider.nestedFieldAutoCompletion(source, undefined, "parent.taskrun.value")).toEqual([])
        })

        it("returns no value suggestions when the yaml element is missing", async () => {
            const provider = new YamlAutoCompletion()

            expect(await provider.valueAutoCompletion("", undefined, undefined)).toEqual([])
            expect(await provider.valueAutoCompletion("namespace:", {}, undefined)).toEqual([])
            expect(await provider.nestedFieldAutoCompletion("namespace:", undefined, "outputs.task1")).toEqual([])
            expect(axiosGet).not.toHaveBeenCalled()
        })

        it("keeps the inherited methods empty and non-throwing on the yaml provider", async () => {
            const provider = new YamlAutoCompletion()

            expect(provider).toBeInstanceOf(PebbleAutoCompletion)
            expect(await provider.rootFieldAutoCompletion({source: "unknown: [", offset: 10})).toEqual([])
            expect(await provider.nestedFieldAutoCompletion("unknown: [", undefined, "unknown.deeply.nested.path")).toEqual([])
            expect(await provider.functionAutoCompletion(undefined, "doesNotExist", {})).toEqual([])
            expect(axiosGet).not.toHaveBeenCalled()
        })
    })
})
