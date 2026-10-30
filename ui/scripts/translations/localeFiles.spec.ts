import {describe, expect, it} from "vitest"

import {parseLocaleModule} from "./localeFiles.mjs"

describe("parseLocaleModule", () => {
    it("shouldReadTheObjectLiteralWhenTheFileIsPlainData", () => {
        const source = `export default {
    // a comment
    "en": {"ks_empty": {"title": 'It\\'s empty', "hint": \`plain\`,},},
};
`

        expect(parseLocaleModule(source)).toEqual({en: {ks_empty: {title: "It's empty", hint: "plain"}}})
    })

    it("shouldDecodeEscapesAndLineContinuationsWhenTheFileUsesCrlf", () => {
        const source = "export default {\r\n    \"en\": {\"a\": \"x\\u00e9\\x41\\n\\/\\\r\n y\"},\r\n}\r\n"

        expect(parseLocaleModule(source)).toEqual({en: {a: "xéA\n/ y"}})
    })

    it("shouldKeepProtoKeyAsPlainPropertyWhenTheFileDefinesOne", () => {
        const parsed = parseLocaleModule("export default {\"__proto__\": {\"polluted\": \"yes\"}}")

        expect(Object.keys(parsed)).toEqual(["__proto__"])
        expect(({} as Record<string, unknown>).polluted).toBeUndefined()
    })

    it.each([
        ["a call", "export default {en: {a: (() => { throw new Error() })()}}"],
        ["a template interpolation", "export default {en: {a: `${process.exit(1)}`}}"],
        ["a statement after the export", "export default {en: {}}; process.exit(1)"],
        ["a spread", "export default {en: {...other}}"],
        ["an import", "import x from \"y\"\nexport default {}"],
        ["a string as the export", "export default \"x\""],
        ["an unterminated string", "export default {en: {a: \"x}}"],
        ["an unterminated comment", "export default {en: {} /* x"],
    ])("shouldThrowWhenTheFileHolds%s", (_, source) => {
        expect(() => parseLocaleModule(source)).toThrow(/Cannot parse the locale file/)
    })
})
