import {describe, expect, it} from "vitest"
import {unknownPropertiesOf} from "../../../../src/components/no-code/components/tasks/unknownProperties"

const logProperties = {id: {}, message: {}, level: {}}

describe("unknownPropertiesOf", () => {
    it("should return the keys the schema does not describe", () => {
        expect(unknownPropertiesOf({id: "hello", message: "Hi", colour: "purple"}, logProperties, false))
            .toEqual([["colour", "purple"]])
    })

    it("should not report the discriminator the form renders on its own", () => {
        expect(unknownPropertiesOf({type: "io.kestra.plugin.core.log.Log", message: "Hi"}, logProperties, false))
            .toEqual([])
    })

    it("should report nothing when the schema accepts additional properties", () => {
        expect(unknownPropertiesOf({colour: "purple"}, logProperties, true)).toEqual([])
    })

    it("should report nothing when the schema is unknown, so nothing can be called unknown", () => {
        expect(unknownPropertiesOf({id: "ghost", colour: "purple"}, undefined, false)).toEqual([])
        expect(unknownPropertiesOf({id: "ghost", colour: "purple"}, {}, false)).toEqual([])
    })

    it("should ignore a value that carries nothing to remove", () => {
        expect(unknownPropertiesOf({colour: null, shade: undefined}, logProperties, false)).toEqual([])
    })

    it("should report an object value so it can be removed whole", () => {
        expect(unknownPropertiesOf({options: {a: 1}}, logProperties, false)).toEqual([["options", {a: 1}]])
    })
})
