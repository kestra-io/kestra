import {describe, expect, test} from "vitest"
import {isMap, Pair, Scalar, YAMLMap} from "yaml"
import {
    parseDocumentTyped,
    scalarKey,
    TOSTRING_OPTIONS,
} from "../../../src/utils/yaml/document.ts"

describe("yaml document utils", () => {
    test("parseDocumentTyped parses a flow with map contents", () => {
        const document = parseDocumentTyped("id: flow\nnamespace: io.kestra.tests\n")

        expect(isMap(document.contents)).toBe(true)
    })

    test("scalarKey returns a scalar string key", () => {
        expect(scalarKey(new Pair(new Scalar("id"), new Scalar("flow")))).toBe("id")
    })

    test("scalarKey returns undefined for a scalar numeric key", () => {
        expect(scalarKey(new Pair(new Scalar(1), new Scalar("flow")))).toBeUndefined()
    })

    test("scalarKey returns a plain string key", () => {
        expect(scalarKey(new Pair("id", "flow"))).toBe("id")
    })

    test("scalarKey returns undefined for a complex key", () => {
        expect(scalarKey(new Pair(new YAMLMap(), new Scalar("flow")))).toBeUndefined()
    })

    test("TOSTRING_OPTIONS keeps long lines unwrapped", () => {
        const source = `description: ${"a long flow description ".repeat(8).trim()}\n`

        expect(parseDocumentTyped(source).toString(TOSTRING_OPTIONS)).toBe(source)
        expect(TOSTRING_OPTIONS.lineWidth).toBe(0)
    })
})
