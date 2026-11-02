import {describe, expect, it} from "vitest"
import {RuleTester} from "eslint"
import vueParser from "vue-eslint-parser"

import eslintPlugin from "./eslintPlugin.mjs"

const template = (body: string) => `<template>\n    ${body}\n</template>\n`

describe("i18n-t-scope", () => {
    it("requires scope=\"global\" on <i18n-t> and adds it", () => {
        const tester = new RuleTester({languageOptions: {parser: vueParser, ecmaVersion: 2022, sourceType: "module"}})

        expect(() => tester.run("i18n-t-scope", eslintPlugin.rules["i18n-t-scope"], {
            valid: [
                {code: template("<i18n-t keypath=\"a\" tag=\"span\" scope=\"global\" />")},
                {code: template("<I18nT keypath=\"a\" :scope=\"scope\" />")},
                {code: template("<i18n-n :value=\"1\" />")},
            ],
            invalid: [
                {
                    code: template("<i18n-t keypath=\"a\" tag=\"span\" />"),
                    output: template("<i18n-t keypath=\"a\" tag=\"span\" scope=\"global\" />"),
                    errors: [{messageId: "missing"}],
                },
                {
                    code: template("<I18nT>x</I18nT>"),
                    output: template("<I18nT scope=\"global\">x</I18nT>"),
                    errors: [{messageId: "missing"}],
                },
                {
                    code: template("<i18n-t\n        keypath=\"a\"\n        tag=\"p\"\n    >x</i18n-t>"),
                    output: template("<i18n-t\n        keypath=\"a\"\n        tag=\"p\"\n        scope=\"global\"\n    >x</i18n-t>"),
                    errors: [{messageId: "missing"}],
                },
            ],
        })).not.toThrow()
    })
})
