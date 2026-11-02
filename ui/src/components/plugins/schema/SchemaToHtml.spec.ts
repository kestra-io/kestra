import {describe, test, expect, vi} from "vitest"
import {defineComponent, h, Suspense} from "vue"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"

import SchemaToHtml from "./SchemaToHtml.vue"
import type {JSONSchema} from "./utils/schemaUtils"

vi.mock("@kestra-io/design-system/shiki", () => ({
    getShiki: vi.fn().mockResolvedValue({
        codeToHtml: () => "<code>mock code</code>",
        getLoadedLanguages: () => ["yaml"],
    }),
    isSpecialLang: () => false,
    loadLanguageOnDemand: vi.fn().mockResolvedValue(false),
}))

// JSONSchema.properties mixes property entries with section keys, which no object literal satisfies.
const SCHEMA = {
    properties: {
        properties: {message: {type: "string"}},
        $examples: [{title: "Log a message", code: "message: hello"}],
        $metrics: [{name: "records", type: "counter"}],
    },
    outputs: {properties: {value: {type: "string"}}},
    definitions: {
        "io.kestra.Example": {title: "Example", properties: {name: {type: "string"}}},
    },
} as unknown as JSONSchema

const GERMAN = {
    plugins: {
        nav_examples: "Beispiele",
        nav_properties: "Eigenschaften",
        nav_outputs: "Ausgaben",
        nav_metrics: "Metriken",
        nav_definitions: "Definitionen",
    },
}

describe("SchemaToHtml", () => {
    test("section headings follow the app language", async () => {
        const wrapper = i18nMount(defineComponent({
            render: () => h(Suspense, null, {default: () => h(SchemaToHtml, {schema: SCHEMA, pluginType: "io.kestra.plugin.core.log.Log", noUrlChange: true})}),
        }), {messages: GERMAN})

        await flushPromises()

        const headings = ["examples", "properties", "outputs", "metrics", "definitions"]
            .map((section) => wrapper.find(`[data-section="${section}"]`).text())
        expect(headings[0]).toContain("Beispiele")
        expect(headings[1]).toContain("Eigenschaften")
        expect(headings[2]).toContain("Ausgaben")
        expect(headings[3]).toContain("Metriken")
        expect(headings[4]).toContain("Definitionen")
        expect(wrapper.text()).not.toMatch(/\b(Examples|Properties|Outputs|Metrics|Definitions)\b/)
    })
})
