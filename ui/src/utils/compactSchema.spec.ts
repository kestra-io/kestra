import {describe, it, expect} from "vitest"
import {inflateCompactSchema} from "./compactSchema"

const ref = (key: string) => ({$ref: `#/definitions/${key}`})
const SUBTYPES = [ref("a.Log"), ref("a.Return")]

describe("inflateCompactSchema", () => {
    it("puts a shared property schema back at every site, as one object", () => {
        const retry = {type: "object", title: "Retry policy"}
        const schema = inflateCompactSchema({
            definitions: {
                "a.Log": {properties: {id: {type: "string"}, retry: ref("shared-property-retry")}},
                "a.Return": {properties: {retry: ref("shared-property-retry")}},
                "shared-property-retry": retry,
            },
        })

        expect(schema.definitions).toEqual({
            "a.Log": {properties: {id: {type: "string"}, retry}},
            "a.Return": {properties: {retry}},
        })
        expect(schema.definitions["a.Log"].properties.retry).toBe(schema.definitions["a.Return"].properties.retry)
    })

    it("puts a subtype list back inline, and one annotated branch per subtype where the list carried an annotation", () => {
        const schema = inflateCompactSchema({
            definitions: {
                "a.Flow": {properties: {
                    tasks: {type: "array", items: ref("shared-subtypes")},
                    single: {title: "One task", $dynamic: true, anyOf: [ref("shared-subtypes")]},
                }},
                "shared-subtypes": {anyOf: SUBTYPES},
            },
        })

        expect(schema.definitions).toEqual({
            "a.Flow": {properties: {
                tasks: {type: "array", items: {anyOf: SUBTYPES}},
                single: {anyOf: SUBTYPES.map(subtype => ({allOf: [subtype, {title: "One task", $dynamic: true}]}))},
            }},
        })
    })

    // `Switch.cases` references the `Task` definition in the plain schema too: only what the compactor moved comes back inline.
    it("follows the alias of a list a definition already held, and leaves a direct reference to that definition alone", () => {
        const schema = inflateCompactSchema({
            definitions: {
                "a.Task": {anyOf: SUBTYPES},
                "a.Flow": {properties: {tasks: {type: "array", items: ref("shared-subtypes")}}},
                "a.Switch": {properties: {cases: {additionalProperties: {type: "array", items: ref("a.Task")}}}},
                "shared-subtypes": ref("a.Task"),
            },
        })

        expect(schema.definitions).toEqual({
            "a.Task": {anyOf: SUBTYPES},
            "a.Flow": {properties: {tasks: {type: "array", items: {anyOf: SUBTYPES}}}},
            "a.Switch": {properties: {cases: {additionalProperties: {type: "array", items: ref("a.Task")}}}},
        })
    })

    it("inflates what a shared schema itself shares", () => {
        const schema = inflateCompactSchema({
            definitions: {
                "a.Log": {properties: {errors: ref("shared-property-errors")}},
                "a.Return": {properties: {errors: ref("shared-property-errors")}},
                "shared-property-errors": {type: "array", items: {title: "On error", anyOf: [ref("shared-subtypes")]}},
                "shared-subtypes": {anyOf: SUBTYPES},
            },
        })

        const errors = {type: "array", items: {anyOf: SUBTYPES.map(subtype => ({allOf: [subtype, {title: "On error"}]}))}}
        expect(schema.definitions).toEqual({
            "a.Log": {properties: {errors}},
            "a.Return": {properties: {errors}},
        })
    })
})
