import {describe, expect, it} from "vitest"

import type {JSONProperty} from "../../../../src/components/plugins/schema/utils/schemaUtils"
import type {RootJsonSchema} from "../../../../src/stores/plugins"

import {
    collectTaskTypes,
    findMetricDescription,
    getSchemaMetrics,
} from "../../../../src/components/executions/metricDescriptions"

describe("metricDescriptions", () => {
    it("collects plugin types from nested tasks and error handlers", () => {
        const taskTypes = collectTaskTypes([
            {
                id: "parent",
                type: "example.Parent",
                tasks: [
                    {
                        id: "child",
                        type: "example.Child",
                    },
                ],
                errors: [
                    {
                        id: "handler",
                        type: "example.ErrorHandler",
                    },
                ],
            },
        ])

        expect(taskTypes.get("parent")).toBe("example.Parent")
        expect(taskTypes.get("child")).toBe("example.Child")
        expect(taskTypes.get("handler")).toBe("example.ErrorHandler")
    })

    it("finds a metric description by name", () => {
        const metrics: JSONProperty[] = [
            {name: "record_count", type: "integer", description: "Number of records processed"},
        ]

        expect(findMetricDescription(metrics, "record_count"))
            .toBe("Number of records processed")
    })

    it("returns undefined when a metric has no description", () => {
        const metrics: JSONProperty[] = [
            {name: "record_count", type: "integer"},
        ]

        expect(findMetricDescription(metrics, "record_count")).toBeUndefined()
        expect(findMetricDescription(metrics, "unknown")).toBeUndefined()
        expect(findMetricDescription(undefined, "record_count")).toBeUndefined()
    })

    it("reads metrics directly from the root schema", () => {
        const metrics: JSONProperty[] = [
            {name: "record_count", type: "integer", description: "Number of records processed"},
        ]

        const schema = {
            $ref: "#/definitions/ExamplePlugin",
            properties: {$metrics: metrics},
            definitions: {},
        } as unknown as RootJsonSchema

        expect(getSchemaMetrics(schema, "ExamplePlugin")).toEqual(metrics)
    })

    it("falls back to metrics in the referenced definition", () => {
        const metrics: JSONProperty[] = [
            {name: "record_count", type: "integer", description: "Number of records processed"},
        ]

        const schema = {
            $ref: "#/definitions/ExamplePlugin",
            definitions: {
                ExamplePlugin: {
                    properties: {$metrics: metrics},
                },
            },
        } as unknown as RootJsonSchema

        expect(getSchemaMetrics(schema, "ExamplePlugin")).toEqual(metrics)
    })
})
