import {describe, it, expect} from "vitest"
import {countUnsetRequiredFields, findRequiredFieldFrames, type PartialSchema} from "../../../src/components/no-code/utils/requiredFields"
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

describe("findRequiredFieldFrames", () => {
    it("returns a frame for a drillable array item, so the jump can open it before focusing the field", () => {
        const itemSchema = {
            type: "object",
            properties: {
                url: {type: "string"},
                connection: {type: "object", properties: {timeout: {type: "string"}}},
            },
            required: ["url"],
        }
        // Guards the premise: this item schema is the drillable case the frame exists for.
        expect(shouldDrillItem(itemSchema, {})).toBe(true)

        const schema = {type: "object", properties: {items: {type: "array", items: itemSchema}}}
        const model = {items: [{url: "set"}, {}]}

        const frames = findRequiredFieldFrames(model, schema, {}, "items[1].url")

        expect(frames).toEqual([{path: "items[1]", label: "#2", schema: itemSchema}])
    })

    it("returns no frame for a non-drillable array item, since TaskArray already renders it inline", () => {
        const itemSchema = {
            type: "object",
            properties: {when: {type: "string"}, message: {type: "string"}},
            required: ["message"],
        }
        expect(shouldDrillItem(itemSchema, {})).toBe(false)

        const schema = {type: "object", properties: {items: {type: "array", items: itemSchema}}}
        const model = {items: [{when: "x"}]}

        expect(findRequiredFieldFrames(model, schema, {}, "items[0].message")).toEqual([])
    })

    it("returns no frame for a top-level field that is already mounted", () => {
        const schema = {type: "object", properties: {message: {type: "string"}}, required: ["message"]}

        expect(findRequiredFieldFrames({}, schema, {}, "message")).toEqual([])
    })

    it("returns undefined when the path cannot be resolved against the schema", () => {
        const schema = {type: "object", properties: {message: {type: "string"}}}

        expect(findRequiredFieldFrames({}, schema, {}, "nope")).toBeUndefined()
    })

    it("nests a frame per drillable array crossed on the way to a deeply nested field", () => {
        const innerItemSchema = {
            type: "object",
            properties: {
                url: {type: "string"},
                metadata: {type: "object", properties: {note: {type: "string"}}},
            },
            required: ["url"],
        }
        const outerItemSchema = {
            type: "object",
            properties: {
                steps: {type: "array", items: innerItemSchema},
                metadata: {type: "object", properties: {note: {type: "string"}}},
            },
        }
        const schema = {type: "object", properties: {jobs: {type: "array", items: outerItemSchema}}}
        const model = {jobs: [{steps: [{}]}]}

        const frames = findRequiredFieldFrames(model, schema, {}, "jobs[0].steps[0].url")

        expect(frames).toEqual([
            {path: "jobs[0]", label: "#1", schema: outerItemSchema},
            {path: "jobs[0].steps[0]", label: "#1", schema: innerItemSchema},
        ])
    })
})
