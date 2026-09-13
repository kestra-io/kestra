import {describe, it, expect} from "vitest"
import {
    looksLikeObject,
    shouldDrillItem,
    summarizeValue,
} from "../../../src/components/no-code/components/tasks/fieldNesting"

const definitions = {
    Foo: {type: "object", properties: {a: {type: "string"}}},
    AssetIdentifier: {type: "object", properties: {id: {type: "string"}, type: {type: "string"}}},
    Input: {anyOf: [{$ref: "#/definitions/Foo"}, {type: "object", properties: {b: {type: "string"}}}]},
}

describe("looksLikeObject", () => {
    it("is true for object / complex / object-anyOf", () => {
        expect(looksLikeObject({type: "object", properties: {a: {}}}, {})).toBe(true)
        expect(looksLikeObject({$ref: "#/definitions/Foo"}, definitions)).toBe(true)
        expect(looksLikeObject({anyOf: [{type: "string"}, {$ref: "#/definitions/Foo"}]}, definitions)).toBe(true)
    })

    it("is false for scalars, maps and scalar anyOf", () => {
        expect(looksLikeObject({type: "string"}, {})).toBe(false)
        expect(looksLikeObject({type: "object", additionalProperties: {}}, {})).toBe(false)
        expect(looksLikeObject({anyOf: [{type: "string"}, {type: "integer"}]}, {})).toBe(false)
    })
})

describe("shouldDrillItem", () => {
    it("keeps a flat scalar record inline", () => {
        expect(shouldDrillItem({type: "object", properties: {id: {type: "string"}, type: {type: "string"}}}, {})).toBe(false)
        expect(shouldDrillItem({allOf: [{$ref: "#/definitions/AssetIdentifier"}, {$dynamic: true}]}, definitions)).toBe(false)
    })

    it("keeps primitives and primitive arrays inline", () => {
        expect(shouldDrillItem({type: "string"}, {})).toBe(false)
        expect(shouldDrillItem({type: "array", items: {type: "string"}}, {})).toBe(false)
    })

    it("drills a polymorphic item, direct or behind a $ref", () => {
        expect(shouldDrillItem({anyOf: [{$ref: "#/definitions/Foo"}, {type: "object", properties: {b: {}}}]}, definitions)).toBe(true)
        expect(shouldDrillItem({$ref: "#/definitions/Input"}, definitions)).toBe(true)
    })

    it("drills an item that nests an object or a list of objects", () => {
        expect(shouldDrillItem({type: "object", properties: {child: {type: "object", properties: {x: {}}}}}, {})).toBe(true)
        expect(shouldDrillItem({type: "object", properties: {list: {type: "array", items: {$ref: "#/definitions/Foo"}}}}, definitions)).toBe(true)
    })
})

describe("summarizeValue", () => {
    it("reports empty", () => {
        expect(summarizeValue(null)).toEqual({kind: "empty"})
        expect(summarizeValue(undefined)).toEqual({kind: "empty"})
        expect(summarizeValue([])).toEqual({kind: "empty"})
        expect(summarizeValue({})).toEqual({kind: "empty"})
        expect(summarizeValue("  ")).toEqual({kind: "empty"})
    })

    it("joins a short primitive array", () => {
        expect(summarizeValue(["FR", "US", "JP"])).toEqual({kind: "text", text: "FR, US, JP"})
    })

    it("counts a long or object array", () => {
        expect(summarizeValue(["a", "b", "c", "d"])).toEqual({kind: "count", count: 4})
        expect(summarizeValue([{}, {}])).toEqual({kind: "count", count: 2})
    })

    it("shows a map as key=value pairs", () => {
        expect(summarizeValue({env: "prod", team: "data"})).toEqual({kind: "text", text: "env=prod, team=data"})
    })

    it("leads a discriminated object with its type", () => {
        expect(summarizeValue({type: "ExecutionStatus", in: ["SUCCESS", "WARNING"]}))
            .toEqual({kind: "text", text: "ExecutionStatus · in: SUCCESS, WARNING"})
    })

    it("shortens a fully-qualified discriminator", () => {
        const value = {type: "io.kestra.core.models.conditions.types.ExecutionStatusCondition", in: ["SUCCESS"]}
        expect(summarizeValue(value)).toEqual({kind: "text", text: "ExecutionStatusCondition · in: SUCCESS"})
    })

    it("keeps scalars verbatim, including falsy ones", () => {
        expect(summarizeValue("hello")).toEqual({kind: "text", text: "hello"})
        expect(summarizeValue(0)).toEqual({kind: "text", text: "0"})
        expect(summarizeValue(false)).toEqual({kind: "text", text: "false"})
    })

    it("truncates long text", () => {
        const long = "x".repeat(80)
        const result = summarizeValue(long)
        expect(result.kind).toBe("text")
        expect((result as {text: string}).text.endsWith("…")).toBe(true)
        expect((result as {text: string}).text.length).toBeLessThanOrEqual(48)
    })
})
