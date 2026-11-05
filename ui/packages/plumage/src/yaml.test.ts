import {describe, expect, it} from "vitest"
import {createHighlighterCore} from "shiki/core"
import {createJavaScriptRegexEngine} from "shiki/engine/javascript"
import {createOnigurumaEngine} from "shiki/engine/oniguruma"
import yaml from "shiki/langs/yaml.mjs"
import {kestraDark, toTextMateTheme, yamlKeysInjection} from "./index.ts"

const {key, constant, comment, value} = kestraDark.syntax

describe("yamlKeysInjection", () => {
    it.each(["javascript", "oniguruma"] as const)("colors keys that read as booleans as keys with the %s engine", async engine => {
        const h = await createHighlighterCore({
            engine: engine === "javascript" ? createJavaScriptRegexEngine() : createOnigurumaEngine(import("shiki/wasm")),
            themes: [toTextMateTheme(kestraDark)],
            langs: [],
        })
        await h.loadLanguage(...yaml, yamlKeysInjection)
        const colorOf = (code: string, word: string) => {
            const tokens = h.codeToTokens(code, {lang: "yaml", theme: "kestra-dark"}).tokens.flat()
            return tokens.find(token => token.content.trim() === word)?.color
        }

        expect(colorOf("- on: OPEN", "on")).toBe(key)
        expect(colorOf("point:\n  y: 2", "y")).toBe(key)
        expect(colorOf("{off: 1}", "off")).toBe(key)
        expect(colorOf("enabled: on", "on")).toBe(constant)
        expect(colorOf("# on: x", "# on: x")).toBe(comment)
        expect(colorOf("script: |\n  on: x", "on: x")).toBe(value)
    })
})
