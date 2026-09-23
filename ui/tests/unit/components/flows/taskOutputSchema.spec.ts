import {describe, it, expect} from "vitest"
import {resolveDeclaredOutputProperties, hasDeclaredOutputs} from "../../../../src/components/flows/taskOutputSchema"

describe("resolveDeclaredOutputProperties", () => {
    it("returns the first candidate that is a non-empty object", () => {
        const properties = {uri: {type: "string"}}
        expect(resolveDeclaredOutputProperties([undefined, properties, {other: {type: "string"}}])).toBe(properties)
    })

    it("returns undefined when every candidate is missing", () => {
        expect(resolveDeclaredOutputProperties([undefined, undefined])).toBeUndefined()
    })
})

describe("hasDeclaredOutputs", () => {
    it("is false when the properties are undefined", () => {
        expect(hasDeclaredOutputs(undefined)).toBe(false)
    })

    it("is false when the schema declares no properties", () => {
        expect(hasDeclaredOutputs({})).toBe(false)
    })

    it("is true when the schema declares at least one output", () => {
        expect(hasDeclaredOutputs({uri: {type: "string"}})).toBe(true)
    })
})
