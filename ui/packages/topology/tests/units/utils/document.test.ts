//ui/packages/topology/tests/units/utils/document.test.ts


import {test, expect, describe} from "vitest"
import {Pair, YAMLMap, parseDocument, Scalar} from "yaml"
import {parseDocumentTyped, scalarKey, TOSTRING_OPTIONS} from "../../../src/utils/yaml/document.ts"

describe("parseDocumentTyped", () => {
    test("parses a flow into a document whose contents is a map", () => {
        const document = parseDocumentTyped(`
id: my-flow
namespace: io.kestra.tests
tasks:
  - id: task
    type: io.kestra.plugin.core.log.Log
`)

        expect(document.contents).toBeInstanceOf(YAMLMap)
    })
})

describe("scalarKey", () => {
    test("returns the string key of a scalar-keyed pair", () => {
        const pair = new Pair(new Scalar("id"), "my-flow")

        expect(scalarKey(pair)).toBe("id")
    })

    test("returns undefined for a scalar key that is not a string", () => {
        const pair = new Pair(new Scalar(123), "value")

        expect(scalarKey(pair)).toBeUndefined()
    })

    test("handles a plain string key that is not wrapped in a scalar", () => {
        const pair = new Pair("id", "my-flow")

        expect(scalarKey(pair)).toBe("id")
    })

    test("returns undefined for a complex key such as a map", () => {
        const key = parseDocument("{foo: bar}").contents
        const pair = new Pair(key, "value")

        expect(scalarKey(pair)).toBeUndefined()
    })
})

describe("TOSTRING_OPTIONS", () => {
    test("sets lineWidth to 0 so long lines are not re-wrapped", () => {
        expect(TOSTRING_OPTIONS.lineWidth).toBe(0)

        const longValue = "a".repeat(200)
        const document = parseDocument(`description: ${longValue}`)

        const output = document.toString(TOSTRING_OPTIONS)

        expect(output).toContain(longValue)
        expect(output.split("\n")).toHaveLength(2)
    })
})
