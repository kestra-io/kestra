import {describe, expect, it} from "vitest"
import {getTaskComponent} from "./getTaskComponent"

// `definitions` is the global, class-name-keyed flow schema map — it has no top-level
// `.properties`, so it must never be the source of a task's own sibling property names.
const globalDefinitions = {
    "io.kestra.plugin.core.flow.Subflow": {properties: {namespace: {type: "string"}, flowId: {type: "string"}}},
    "io.kestra.plugin.kestra.dashboards.Export": {properties: {dashboardId: {type: "string"}, chartId: {type: "string"}}},
}

describe("getTaskComponent sibling-key dispatch", () => {
    it("dispatches flowId to subflow-id when siblingKeys includes namespace", () => {
        const component = getTaskComponent({type: "string"}, globalDefinitions, "flowId", ["namespace", "flowId"])
        expect(component.ksTaskName).toBe("subflow-id")
    })

    it("does not dispatch flowId to subflow-id without namespace in siblingKeys", () => {
        const component = getTaskComponent({type: "string"}, globalDefinitions, "flowId", ["flowId"])
        expect(component.ksTaskName).not.toBe("subflow-id")
    })

    it("falls back to plain string when siblingKeys is omitted", () => {
        const component = getTaskComponent({type: "string"}, globalDefinitions, "flowId")
        expect(component.ksTaskName).not.toBe("subflow-id")
    })

    it("dispatches chartId to chart-id when siblingKeys includes dashboardId", () => {
        const component = getTaskComponent({type: "string"}, globalDefinitions, "chartId", ["dashboardId", "chartId"])
        expect(component.ksTaskName).toBe("chart-id")
    })

    it("does not dispatch chartId to chart-id without dashboardId in siblingKeys", () => {
        const component = getTaskComponent({type: "string"}, globalDefinitions, "chartId", ["chartId"])
        expect(component.ksTaskName).not.toBe("chart-id")
    })

    it("dispatches dashboardId to dashboard-id regardless of siblingKeys", () => {
        const component = getTaskComponent({type: "string"}, globalDefinitions, "dashboardId")
        expect(component.ksTaskName).toBe("dashboard-id")
    })
})

// One class carries a `const` discriminator, the other an `enum` alias — both are real shapes in
// the AIAgent schema (kestra-io/plugin-ai#408) and must be treated identically.
const providerDefinitions = {
    "io.kestra.plugin.ai.provider.GoogleGemini": {
        type: "object",
        properties: {type: {const: "io.kestra.plugin.ai.provider.GoogleGemini"}, modelName: {type: "string"}},
        required: ["modelName"],
    },
    "io.kestra.plugin.ai.provider.OpenAI": {
        type: "object",
        properties: {
            type: {enum: ["io.kestra.plugin.ai.provider.OpenAI", "io.kestra.plugin.langchain4j.provider.OpenAI"]},
            modelName: {type: "string"},
        },
    },
}

describe("getTaskComponent discriminated-union dispatch", () => {
    it("dispatches an anyOf of discriminated $refs to the plugin-implementation control", () => {
        const property = {
            anyOf: [
                {$ref: "#/definitions/io.kestra.plugin.ai.provider.GoogleGemini"},
                {$ref: "#/definitions/io.kestra.plugin.ai.provider.OpenAI"},
            ],
        }
        const component = getTaskComponent(property, providerDefinitions)
        expect(component.ksTaskName).toBe("plugin-implementation")
    })

    it("dispatches an array whose items.anyOf are discriminated $refs to the plugin-implementation control", () => {
        const property = {
            type: "array",
            items: {
                anyOf: [
                    {$ref: "#/definitions/io.kestra.plugin.ai.provider.GoogleGemini"},
                    {$ref: "#/definitions/io.kestra.plugin.ai.provider.OpenAI"},
                ],
            },
        }
        const component = getTaskComponent(property, providerDefinitions, "tools")
        expect(component.ksTaskName).toBe("plugin-implementation")
    })

    it("dispatches a lone $ref whose definition is discriminated to the plugin-implementation control", () => {
        const property = {$ref: "#/definitions/io.kestra.plugin.ai.provider.GoogleGemini"}
        const component = getTaskComponent(property, providerDefinitions)
        expect(component.ksTaskName).toBe("plugin-implementation")
    })

    it("treats an enum discriminator the same as a const one", () => {
        const property = {$ref: "#/definitions/io.kestra.plugin.ai.provider.OpenAI"}
        const component = getTaskComponent(property, providerDefinitions)
        expect(component.ksTaskName).toBe("plugin-implementation")
    })

    it("does not treat an anyOf of inline scalar types as an implementation picker", () => {
        const property = {anyOf: [{type: "integer"}, {type: "string"}]}
        const component = getTaskComponent(property, {})
        expect(component.ksTaskName).not.toBe("plugin-implementation")
    })

    it("does not treat a lone $ref without a discriminated type as an implementation picker", () => {
        const definitions = {"io.kestra.plugin.ai.ChatConfiguration": {type: "object", properties: {temperature: {type: "number"}}}}
        const property = {$ref: "#/definitions/io.kestra.plugin.ai.ChatConfiguration"}
        const component = getTaskComponent(property, definitions)
        expect(component.ksTaskName).toBe("complex")
    })

    it("still dispatches the tasks section to the list control even when its items look like a discriminated union", () => {
        const property = {
            type: "array",
            items: {
                anyOf: [
                    {$ref: "#/definitions/io.kestra.plugin.ai.provider.GoogleGemini"},
                    {$ref: "#/definitions/io.kestra.plugin.ai.provider.OpenAI"},
                ],
            },
        }
        const component = getTaskComponent(property, providerDefinitions, "tasks")
        expect(component.ksTaskName).toBe("list")
    })

    it("still dispatches a $ref to the abstract Task base to the task control", () => {
        const property = {$ref: "#/definitions/io.kestra.core.models.tasks.Task"}
        const component = getTaskComponent(property, {})
        expect(component.ksTaskName).toBe("task")
    })

    it("dispatches taskRunner through the discriminated-union rule instead of a hardcoded key check", () => {
        const runnerDefinitions = {
            "io.kestra.plugin.scripts.runner.docker.Docker": {type: "object", properties: {type: {const: "io.kestra.plugin.scripts.runner.docker.Docker"}}},
            "io.kestra.plugin.scripts.runner.process.Process": {type: "object", properties: {type: {const: "io.kestra.plugin.scripts.runner.process.Process"}}},
        }
        const property = {
            anyOf: [
                {$ref: "#/definitions/io.kestra.plugin.scripts.runner.docker.Docker"},
                {$ref: "#/definitions/io.kestra.plugin.scripts.runner.process.Process"},
            ],
        }
        const component = getTaskComponent(property, runnerDefinitions, "taskRunner")
        expect(component.ksTaskName).toBe("plugin-implementation")
    })

    it("does not sweep up a core closed-set union discriminated by a short @JsonSubTypes name, like Retry", () => {
        // AbstractRetry: @JsonSubTypes.Type(value = Constant.class, name = "constant"), etc. — a
        // fixed, non-plugin set with its own established inline-switch UI (kestra#20012 E2E fallout).
        const retryDefinitions = {
            Constant: {type: "object", properties: {type: {const: "constant"}, interval: {type: "string"}}},
            Exponential: {type: "object", properties: {type: {const: "exponential"}, interval: {type: "string"}}},
            Random: {type: "object", properties: {type: {const: "random"}}},
        }
        const property = {
            anyOf: [
                {$ref: "#/definitions/Constant"},
                {$ref: "#/definitions/Exponential"},
                {$ref: "#/definitions/Random"},
            ],
        }
        const component = getTaskComponent(property, retryDefinitions, "retry")
        expect(component.ksTaskName).not.toBe("plugin-implementation")
    })

    it("keeps the counted-header list for a large short-name-discriminated array, like flow Input", () => {
        // Input: @JsonSubTypes.Type(name = "STRING"/"INT"/.../"ARRAY") — 12+ short-named core types,
        // not plugin-provided, so it needs the > 10 list fallback the plugin-implementation rule skips.
        const inputDefinitions = Object.fromEntries(
            ["STRING", "INT", "FLOAT", "BOOLEAN", "DATETIME", "DATE", "TIME", "DURATION", "FILE", "JSON", "ARRAY"]
                .map((name) => [name, {type: "object", properties: {type: {const: name}}}]),
        )
        const property = {
            type: "array",
            items: {anyOf: Object.keys(inputDefinitions).map((name) => ({$ref: `#/definitions/${name}`}))},
        }
        const component = getTaskComponent(property, inputDefinitions, "inputs")
        expect(component.ksTaskName).toBe("list")
    })
})
