import {describe, it, expect} from "vitest"
import {countUnsetRequiredFields, type PartialSchema} from "../../../src/components/no-code/utils/requiredFields"

describe("countUnsetRequiredFields", () => {
    it("counts a required subfield left unset inside a drillable array item, without drilling into it", () => {
        const schema = {
            type: "array",
            items: {
                type: "object",
                properties: {
                    when: {type: "string"},
                    message: {type: "string"},
                },
                required: ["when", "message"],
            },
        }

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

    it("returns nothing for a schema-less or model-less input", () => {
        expect(countUnsetRequiredFields(undefined, undefined, {})).toEqual([])
        expect(countUnsetRequiredFields({}, {type: "object"}, {})).toEqual([])
    })
})
