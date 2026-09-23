import {describe, it, expect} from "vitest"
import {countUnsetRequiredFields, type PartialSchema} from "../../../src/components/no-code/utils/requiredFields"
import {shouldDrillItem} from "../../../src/components/no-code/components/tasks/fieldNesting"

describe("countUnsetRequiredFields", () => {
    it("counts a required subfield left unset inside a drillable array item, without drilling into it", () => {
        const itemSchema = {
            type: "object",
            properties: {
                when: {type: "string"},
                message: {type: "string"},
                metadata: {type: "object", properties: {note: {type: "string"}}},
            },
            required: ["when", "message"],
        }
        // Guards the premise: a scalar-only item never drills (TaskArray renders it inline already).
        expect(shouldDrillItem(itemSchema, {})).toBe(true)

        const schema = {type: "array", items: itemSchema}

        const model = [
            {when: "{{ true }}", message: "ok"},
            {when: "{{ false }}"},
        ]

        const result = countUnsetRequiredFields(model, schema, {})

        expect(result).toEqual([{path: "[1].message", label: "message"}])
    })

    it("does not flag a fully-populated array item", () => {
        const schema = {
            type: "array",
            items: {
                type: "object",
                properties: {when: {type: "string"}},
                required: ["when"],
            },
        }

        const result = countUnsetRequiredFields([{when: "x"}], schema, {})

        expect(result).toEqual([])
    })

    it("recurses into a nested object that is set but leaves its own required subfield unset", () => {
        const schema = {
            type: "object",
            properties: {
                retry: {
                    type: "object",
                    properties: {maxAttempt: {type: "integer"}},
                    required: ["maxAttempt"],
                },
            },
            required: [],
        }

        const result = countUnsetRequiredFields({retry: {}}, schema, {})

        expect(result).toEqual([{path: "retry.maxAttempt", label: "maxAttempt"}])
    })

    it("merges properties and required across allOf branches, resolving $ref", () => {
        const definitions = {
            Check: {
                type: "object",
                properties: {when: {type: "string"}, message: {type: "string"}},
                required: ["when", "message"],
            },
        }
        const schema = {allOf: [{$ref: "#/definitions/Check"}, {$dynamic: false}]}

        const result = countUnsetRequiredFields({}, schema, definitions)

        expect(result.map((f) => f.label).sort()).toEqual(["message", "when"])
    })

    it("resolves the anyOf branch matching the value's discriminator", () => {
        const schema: PartialSchema = {
            anyOf: [
                {
                    type: "object",
                    properties: {type: {const: "A"}, foo: {type: "string"}},
                    required: ["foo"],
                },
                {
                    type: "object",
                    properties: {type: {const: "B"}, bar: {type: "string"}},
                    required: ["bar"],
                },
            ],
        }

        const result = countUnsetRequiredFields({type: "B"}, schema, {})

        expect(result).toEqual([{path: "bar", label: "bar"}])
    })

    it("carries the required from an anyOf branch shaped as allOf: [{$ref}, {required}]", () => {
        const definitions = {A: {type: "object", properties: {a: {type: "string"}, extra: {type: "string"}}, required: ["a"]}}
        const schema = {anyOf: [{allOf: [{$ref: "#/definitions/A"}, {required: ["extra"]}]}]}

        const result = countUnsetRequiredFields({a: "set"}, schema, definitions)

        expect(result).toEqual([{path: "extra", label: "extra"}])
    })

    it("flags a required key that has no matching entry in properties", () => {
        const schema = {type: "object", properties: {a: {type: "string"}}, required: ["a", "b"]}

        const result = countUnsetRequiredFields({a: "set"}, schema, {})

        expect(result).toEqual([{path: "b", label: "b"}])
    })

    it("returns nothing for a schema-less or model-less input", () => {
        expect(countUnsetRequiredFields(undefined, undefined, {})).toEqual([])
        expect(countUnsetRequiredFields({}, {type: "object"}, {})).toEqual([])
    })
})
