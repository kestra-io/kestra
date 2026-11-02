import {describe, expect, it} from "vitest"
import {
    branchDisplayName,
    findBranchByType,
    getImplementationBranches,
    humanizeClassName,
    humanizePropertyKey,
    newImplementation,
    resolveImplementationLabel,
    summarizeImplementationValue,
} from "./discriminatedUnion"
import type {Schema} from "./getTaskComponent"

describe("humanizeClassName", () => {
    it("splits a plain PascalCase name", () => {
        expect(humanizeClassName("GoogleGemini")).toBe("Google Gemini")
    })

    it("keeps a leading acronym intact", () => {
        expect(humanizeClassName("KestraKVStore")).toBe("Kestra KV Store")
    })

    it("splits every word boundary, not just the first", () => {
        expect(humanizeClassName("TavilyWebSearch")).toBe("Tavily Web Search")
    })
})

describe("humanizePropertyKey", () => {
    it("sentence-cases a camelCase key", () => {
        expect(humanizePropertyKey("contentRetrievers")).toBe("Content retrievers")
    })

    it("capitalises a single-word key", () => {
        expect(humanizePropertyKey("tools")).toBe("Tools")
    })
})

const providerDefinitions = {
    "io.kestra.plugin.ai.provider.GoogleGemini": {
        type: "object",
        title: "Language model provider",
        properties: {type: {const: "io.kestra.plugin.ai.provider.GoogleGemini"}, modelName: {type: "string"}},
        required: ["modelName"],
    },
    "io.kestra.plugin.ai.provider.OpenAI": {
        type: "object",
        title: "Language model provider",
        properties: {type: {const: "io.kestra.plugin.ai.provider.OpenAI"}, modelName: {type: "string"}},
        required: ["modelName"],
    },
}

describe("resolveImplementationLabel", () => {
    it("uses the title every branch shares", () => {
        const branches = getImplementationBranches(
            {anyOf: [{$ref: "#/definitions/io.kestra.plugin.ai.provider.GoogleGemini"}, {$ref: "#/definitions/io.kestra.plugin.ai.provider.OpenAI"}]},
            providerDefinitions,
        )!
        expect(resolveImplementationLabel(branches, "provider")).toBe("Language model provider")
    })

    it("falls back to the humanised property key when branches carry no shared title", () => {
        const definitions = {
            "io.kestra.plugin.ai.retriever.TavilyWebSearch": {type: "object", properties: {type: {const: "io.kestra.plugin.ai.retriever.TavilyWebSearch"}}},
            "io.kestra.plugin.ai.retriever.EmbeddingStoreRetriever": {type: "object", properties: {type: {const: "io.kestra.plugin.ai.retriever.EmbeddingStoreRetriever"}}},
        }
        const branches = getImplementationBranches(
            {type: "array", items: {anyOf: [{$ref: "#/definitions/io.kestra.plugin.ai.retriever.TavilyWebSearch"}, {$ref: "#/definitions/io.kestra.plugin.ai.retriever.EmbeddingStoreRetriever"}]}},
            definitions,
        )!
        expect(resolveImplementationLabel(branches, "contentRetrievers")).toBe("Content retrievers")
    })
})

describe("findBranchByType", () => {
    it("finds the branch whose discriminator matches, including through an enum alias", () => {
        const branches = getImplementationBranches(
            {$ref: "#/definitions/io.kestra.plugin.ai.provider.OpenAI"},
            providerDefinitions,
        )!
        expect(findBranchByType(branches, "io.kestra.plugin.ai.provider.OpenAI")?.ref).toBe("io.kestra.plugin.ai.provider.OpenAI")
    })

    it("returns undefined for a type no branch declares", () => {
        const branches = getImplementationBranches(
            {$ref: "#/definitions/io.kestra.plugin.ai.provider.OpenAI"},
            providerDefinitions,
        )!
        expect(findBranchByType(branches, "io.kestra.plugin.ai.provider.Groq")).toBeUndefined()
    })
})

describe("summarizeImplementationValue", () => {
    const definition = {
        type: "object",
        properties: {
            type: {const: "io.kestra.plugin.ai.provider.GoogleGemini"},
            modelName: {type: "string"},
            apiKey: {type: "string", $secret: true},
            baseUrl: {type: "string"},
        },
        required: ["modelName"],
    }

    it("returns empty when nothing is set", () => {
        expect(summarizeImplementationValue({type: "io.kestra.plugin.ai.provider.GoogleGemini"}, definition)).toEqual({kind: "empty"})
    })

    it("puts the required property first and marks a secret as set without its value", () => {
        const value = {type: "io.kestra.plugin.ai.provider.GoogleGemini", apiKey: "{{ secret('KEY') }}", modelName: "gemini-2.5-flash-lite"}
        const summary = summarizeImplementationValue(value, definition)
        expect(summary).toEqual({
            kind: "parts",
            parts: [
                {key: "modelName", secret: false, text: "gemini-2.5-flash-lite"},
                {key: "apiKey", secret: true, text: undefined},
            ],
            overflow: 0,
        })
    })

    it("skips the type property and caps at three parts", () => {
        const value = {type: "x", modelName: "a", apiKey: "s", baseUrl: "b", caPem: "c"}
        const withCaPem = {
            ...definition,
            properties: {...definition.properties, caPem: {type: "string"}},
        }
        const summary = summarizeImplementationValue(value, withCaPem)
        expect(summary.kind).toBe("parts")
        if (summary.kind === "parts") {
            expect(summary.parts).toHaveLength(3)
            expect(summary.parts.some((part) => part.key === "type")).toBe(false)
            expect(summary.overflow).toBe(1)
        }
    })
})

describe("resolveImplementationLabel with real-shaped schemas", () => {
    const definitions: Record<string, Schema> = {
        "io.kestra.plugin.ai.provider.OpenAI": {title: "Use OpenAI models", properties: {type: {const: "io.kestra.plugin.ai.provider.OpenAI"}}},
        "io.kestra.plugin.ai.provider.Ollama": {title: "Use local Ollama models", properties: {type: {const: "io.kestra.plugin.ai.provider.Ollama"}}},
    }

    it("prefers the shared anyOf branch title over the per-implementation definition titles", () => {
        const property: Schema = {
            anyOf: [
                {$ref: "#/definitions/io.kestra.plugin.ai.provider.OpenAI", title: "Language model provider"},
                {$ref: "#/definitions/io.kestra.plugin.ai.provider.Ollama", title: "Language model provider"},
            ],
        }
        const branches = getImplementationBranches(property, definitions)!
        expect(resolveImplementationLabel(branches, "provider")).toBe("Language model provider")
    })

    it("falls back to the humanised key when the branches share no title", () => {
        const property: Schema = {anyOf: [{$ref: "#/definitions/io.kestra.plugin.ai.provider.OpenAI"}, {$ref: "#/definitions/io.kestra.plugin.ai.provider.Ollama"}]}
        const branches = getImplementationBranches(property, definitions)!
        expect(resolveImplementationLabel(branches, "contentRetrievers")).toBe("Content retrievers")
    })
})

describe("plugin-discriminator guard", () => {
    it("does not treat a short, non-plugin @JsonSubTypes name as an implementation picker", () => {
        // Shaped like AbstractRetry: @JsonSubTypes.Type(name = "constant"/"exponential"/"random") —
        // a closed, compile-time set with its own established UI, not a plugin-provided union.
        const definitions: Record<string, Schema> = {
            Constant: {properties: {type: {const: "constant"}, interval: {type: "string"}}},
            Exponential: {properties: {type: {const: "exponential"}, interval: {type: "string"}}},
            Random: {properties: {type: {const: "random"}}},
        }
        const property: Schema = {
            anyOf: [
                {$ref: "#/definitions/Constant"},
                {$ref: "#/definitions/Exponential"},
                {$ref: "#/definitions/Random"},
            ],
        }
        expect(getImplementationBranches(property, definitions)).toBeUndefined()
    })

    it("still matches when every branch resolves to a dotted fully-qualified class name", () => {
        const branches = getImplementationBranches(
            {anyOf: [{$ref: "#/definitions/io.kestra.plugin.ai.provider.GoogleGemini"}, {$ref: "#/definitions/io.kestra.plugin.ai.provider.OpenAI"}]},
            providerDefinitions,
        )
        expect(branches).toHaveLength(2)
    })
})

describe("summarizeImplementationValue with nested implementations", () => {
    const definition = {
        required: ["embeddingProvider", "embeddings"],
        properties: {
            type: {const: "EmbeddingStoreRetriever"},
            embeddingProvider: {anyOf: []},
            embeddings: {anyOf: []},
            maxResults: {type: "integer"},
        },
    }

    const textsOf = (value: Record<string, unknown>) => {
        const summary = summarizeImplementationValue(value, definition as unknown as Schema)
        return summary.kind === "parts" ? summary.parts.map((part) => part.text) : undefined
    }

    it("names a nested implementation instead of stringifying the object", () => {
        expect(textsOf({
            embeddingProvider: {type: "io.kestra.plugin.ai.provider.GoogleGemini"},
            embeddings: {type: "io.kestra.plugin.ai.embeddings.PGVector"},
        })).toEqual(["Google Gemini", "PG Vector"])
    })

    it("leaves out a value with no usable one-line form", () => {
        expect(textsOf({embeddingProvider: {noType: true}, maxResults: 3})).toEqual(["3"])
    })
})

describe("a generic plugin class with one definition per type argument", () => {
    const KPI = "io.kestra.plugin.core.dashboard.chart.KPI"
    const executionsKey = `${KPI}_io.kestra.plugin.core.dashboard.data.IExecutions-Fields___`
    const flowsKey = `${KPI}_io.kestra.plugin.core.dashboard.data.IFlows-Fields___`
    const definitions: Record<string, Schema> = {
        [executionsKey]: {type: "object", properties: {type: {const: KPI}, data: {$ref: "#/definitions/io.kestra.plugin.core.dashboard.data.ExecutionsKPI"}}},
        [flowsKey]: {type: "object", properties: {type: {const: KPI}, data: {$ref: "#/definitions/io.kestra.plugin.core.dashboard.data.FlowsKPI"}}},
        "io.kestra.plugin.core.dashboard.data.ExecutionsKPI": {type: "object", properties: {type: {const: "io.kestra.plugin.core.dashboard.data.ExecutionsKPI"}}},
        "io.kestra.plugin.core.dashboard.data.FlowsKPI": {type: "object", properties: {type: {const: "io.kestra.plugin.core.dashboard.data.FlowsKPI"}}},
    }
    const branches = getImplementationBranches({anyOf: [{$ref: `#/definitions/${executionsKey}`}, {$ref: `#/definitions/${flowsKey}`}]}, definitions) ?? []

    it("uses the discriminator as the type, not the mangled definition key", () => {
        expect(branches.map((branch) => branch.type)).toEqual([KPI, KPI])
    })

    it("tells the branches apart by the data source in their name", () => {
        expect(branches.map((branch) => branchDisplayName(branch, branches))).toEqual(["KPI (Executions KPI)", "KPI (Flows KPI)"])
    })

    it("starts a picked branch with its type and the data source that identifies it", () => {
        expect(newImplementation(branches[1], branches)).toEqual({type: KPI, data: {type: "io.kestra.plugin.core.dashboard.data.FlowsKPI"}})
    })

    it("keeps the chart's own properties and swaps only the data source when the data type changes", () => {
        const previous = {id: "my_kpi", type: KPI, chartOptions: {displayName: "KPI"}, data: {type: "io.kestra.plugin.core.dashboard.data.ExecutionsKPI", columns: {value: {field: "ID"}}}}

        expect(newImplementation(branches[1], branches, previous)).toEqual({
            id: "my_kpi",
            type: KPI,
            chartOptions: {displayName: "KPI"},
            data: {type: "io.kestra.plugin.core.dashboard.data.FlowsKPI"},
        })
    })

    it("does not carry properties over from a different class", () => {
        const previous = {id: "my_pie", type: "io.kestra.plugin.core.dashboard.chart.Pie", data: {type: "io.kestra.plugin.core.dashboard.data.ExecutionsKPI"}}

        expect(newImplementation(branches[1], branches, previous)).toEqual({type: KPI, data: {type: "io.kestra.plugin.core.dashboard.data.FlowsKPI"}})
    })

    it("resolves a stored value to the branch of its data source", () => {
        const found = findBranchByType(branches, KPI, {type: KPI, data: {type: "io.kestra.plugin.core.dashboard.data.FlowsKPI"}})

        expect(found?.ref).toBe(flowsKey)
    })

    it("keeps a plain class name unqualified and picks it with just its type", () => {
        const plain = getImplementationBranches({anyOf: [{$ref: "#/definitions/io.kestra.plugin.ai.provider.OpenAI"}, {$ref: "#/definitions/io.kestra.plugin.ai.provider.GoogleGemini"}]}, providerDefinitions) ?? []

        expect(branchDisplayName(plain[0], plain)).toBe("Open AI")
        expect(newImplementation(plain[0], plain)).toEqual({type: "io.kestra.plugin.ai.provider.OpenAI"})
    })
})
