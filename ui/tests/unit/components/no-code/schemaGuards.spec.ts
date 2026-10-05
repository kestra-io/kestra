import {describe, expect, it} from "vitest"
import {isSchema, isSchemaRecord} from "../../../../src/components/no-code/components/tasks/getTaskComponent"

// Shaped like the plugin documentation response: `schema.properties` is the class's own JSON schema,
// and `schema.definitions` maps class names to schemas whose properties carry `$required`.
const pluginPropertiesSchema = {
    title: "Log a message in the task logs.",
    type: "object",
    required: ["id", "type", "message"],
    properties: {
        id: {type: "string", $required: true},
        type: {const: "io.kestra.plugin.core.log.Log", $required: true},
        message: {
            $required: true,
            anyOf: [
                {type: "string"},
                {type: "array", items: {type: "string"}},
            ],
        },
        level: {
            $required: false,
            allOf: [{$ref: "#/definitions/org.slf4j.event.Level"}],
            default: "INFO",
        },
        workerGroup: {$ref: "#/definitions/io.kestra.core.models.tasks.WorkerGroup", $required: false},
    },
}

const pluginDefinitions = {
    "org.slf4j.event.Level": {type: "string", enum: ["ERROR", "WARN", "INFO", "DEBUG", "TRACE"]},
    "io.kestra.core.models.tasks.WorkerGroup": {
        type: "object",
        required: ["key"],
        properties: {
            key: {type: "string", $required: true},
            fallback: {type: "string", enum: ["FAIL", "WAIT", "CANCEL"], $required: false},
        },
    },
}

describe("isSchema", () => {
    it("accepts a nested plugin schema", () => {
        expect(isSchema(pluginPropertiesSchema)).toBe(true)
    })

    it("accepts properties named after schema keywords", () => {
        const schema = {
            type: "object",
            required: ["type", "items"],
            properties: {
                type: {const: "io.kestra.plugin.core.http.Request"},
                items: {type: "array", items: {type: "string"}},
                properties: {type: "object", additionalProperties: {type: "string"}},
                required: {type: "boolean"},
                anyOf: {type: "string"},
            },
        }

        expect(isSchema(schema)).toBe(true)
    })

    it.each([
        ["null", null],
        ["a string", "io.kestra.plugin.core.log.Log"],
        ["an array", [{type: "string"}]],
        ["a numeric $ref", {$ref: 42}],
        ["a non-array required", {type: "object", required: "id"}],
        ["a non-string type", {type: {const: "io.kestra.plugin.core.log.Log"}}],
        ["a malformed nested property", {properties: {message: {anyOf: {type: "string"}}}}],
    ])("rejects %s", (_label, value) => {
        expect(isSchema(value)).toBe(false)
    })
})

describe("isSchemaRecord", () => {
    it("accepts plugin definitions", () => {
        expect(isSchemaRecord(pluginDefinitions)).toBe(true)
    })

    it("rejects definitions where one entry is malformed", () => {
        expect(isSchemaRecord({...pluginDefinitions, broken: {$ref: 42}})).toBe(false)
    })

    it("rejects a non-object", () => {
        expect(isSchemaRecord(undefined)).toBe(false)
    })
})
