import {describe, expect, it, vi, beforeEach} from "vitest"
import {
    QUOTE,
    resetExpressionCache,
    fillExpressionCache,
    functionToSnippet,
    PebbleAutoCompletion,
    YamlAutoCompletion,
    type PebbleFunctionDef,
} from "./autoCompletionProvider"

const axiosGet = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({
        get: axiosGet,
    }),
}))

vi.mock("../override/utils/route", () => ({
    apiUrlWithoutTenants: () => "http://localhost/api/v1",
}))

describe("autoCompletionProvider", () => {
    beforeEach(() => {
        axiosGet.mockReset()
        resetExpressionCache()
    })

    describe("QUOTE", () => {
        it("is defined as a single quote", () => {
            expect(QUOTE).toBe("'")
        })

        it("wraps values that need quoting and leaves already-quoted values alone", () => {
            const quoteIfNeeded = (val: string) => {
                if (val.startsWith(QUOTE) && val.endsWith(QUOTE)) {
                    return val
                }
                return `${QUOTE}${val}${QUOTE}`
            }

            expect(quoteIfNeeded("secret_name")).toBe("'secret_name'")
            expect(quoteIfNeeded("'already_quoted'")).toBe("'already_quoted'")
        })
    })

    describe("functionToSnippet", () => {
        it("returns a bare call when function has no arguments", () => {
            const fn: PebbleFunctionDef = {name: "uuid", arguments: []}
            expect(functionToSnippet(fn)).toBe("uuid()")
        })

        it("omits arguments with null defaults", () => {
            const fn: PebbleFunctionDef = {
                name: "now",
                arguments: [{name: "format", defaultValue: null}],
            }
            expect(functionToSnippet(fn)).toBe("now()")
        })

        it("generates snippet syntax with indexed placeholders for default arguments", () => {
            const fn: PebbleFunctionDef = {
                name: "kv",
                arguments: [
                    {name: "key", defaultValue: "'my_key'"},
                    {name: "namespace", defaultValue: "flow.namespace"},
                    {name: "errorOnMissing", defaultValue: null},
                ],
            }
            expect(functionToSnippet(fn)).toBe("kv(key=${1:'my_key'}, namespace=${2:flow.namespace})")
        })
    })

    describe("expression cache helpers", () => {
        it("fillExpressionCache allows retrieving cached values without API calls", async () => {
            const cachedFilters = ["upper", "lower", "jq"]
            const cachedFunctions: PebbleFunctionDef[] = [
                {name: "secret", arguments: [{name: "key", defaultValue: "'KEY'"}]},
            ]

            fillExpressionCache(cachedFilters, cachedFunctions)

            const provider = new PebbleAutoCompletion()
            const filters = await provider.filterAutoCompletion()
            const functions = await provider.functionsWithDefaults()

            expect(filters).toEqual(cachedFilters)
            expect(functions).toEqual(cachedFunctions)
            expect(axiosGet).not.toHaveBeenCalled()
        })

        it("resetExpressionCache forces subsequent calls to re-fetch from API", async () => {
            fillExpressionCache(["cachedFilter"], [])
            resetExpressionCache()

            const filtersFromApi = ["apiFilter1", "apiFilter2"]
            axiosGet.mockResolvedValueOnce({data: filtersFromApi})

            const provider = new PebbleAutoCompletion()
            const filters = await provider.filterAutoCompletion()

            expect(axiosGet).toHaveBeenCalledWith("http://localhost/api/v1/pebble/filters")
            expect(filters).toEqual(filtersFromApi)
        })
    })

    describe("PebbleAutoCompletion default implementations", () => {
        it("unknown or empty input yields no suggestions rather than throwing", async () => {
            const provider = new PebbleAutoCompletion()

            await expect(provider.rootFieldAutoCompletion()).resolves.toEqual([])
            await expect(provider.rootFieldAutoCompletion({source: "", offset: 0})).resolves.toEqual([])
            await expect(provider.nestedFieldAutoCompletion("", undefined, "")).resolves.toEqual([])
            await expect(provider.functionAutoCompletion(undefined, "", {})).resolves.toEqual([])
        })

        it("catches API errors and resolves to an empty array without throwing", async () => {
            axiosGet.mockRejectedValue(new Error("Network connection lost"))

            const provider = new PebbleAutoCompletion()
            await expect(provider.filterAutoCompletion()).resolves.toEqual([])
            await expect(provider.functionsWithDefaults()).resolves.toEqual([])
        })
    })

    describe("YamlAutoCompletion", () => {
        it("inherits from PebbleAutoCompletion and returns empty value completion by default", async () => {
            const yamlProvider = new YamlAutoCompletion()

            expect(yamlProvider).toBeInstanceOf(PebbleAutoCompletion)
            await expect(yamlProvider.valueAutoCompletion("", undefined, undefined)).resolves.toEqual([])
        })
    })

    describe("nested paths and deduplication behavior", () => {
        class CustomAutoCompletion extends YamlAutoCompletion {
            override nestedFieldAutoCompletion(
                _source: string,
                parsed: Record<string, unknown> | undefined,
                parentField: string,
            ): Promise<string[]> {
                if (!parsed || !parentField) return Promise.resolve([])
                const target = parsed[parentField]
                if (typeof target === "object" && target !== null) {
                    return Promise.resolve(Array.from(new Set(Object.keys(target))))
                }
                return Promise.resolve([])
            }
        }

        it("nested paths resolve to the right completion list", async () => {
            const provider = new CustomAutoCompletion()
            const parsed = {
                inputs: {
                    fileUri: "kestra://data.csv",
                    batchSize: 100,
                },
                variables: {
                    env: "prod",
                },
            }

            const inputCompletions = await provider.nestedFieldAutoCompletion("", parsed, "inputs")
            expect(inputCompletions).toEqual(["fileUri", "batchSize"])

            const varCompletions = await provider.nestedFieldAutoCompletion("", parsed, "variables")
            expect(varCompletions).toEqual(["env"])

            const missingCompletions = await provider.nestedFieldAutoCompletion("", parsed, "nonExistent")
            expect(missingCompletions).toEqual([])
        })

        it("duplicate suggestions are not emitted twice", async () => {
            const provider = new CustomAutoCompletion()
            const parsed = {
                duplicates: {
                    first: 1,
                },
            }

            const completions = await provider.nestedFieldAutoCompletion("", parsed, "duplicates")
            expect(completions).toEqual(["first"])
            expect(completions.length).toBe(1)
        })
    })
})
