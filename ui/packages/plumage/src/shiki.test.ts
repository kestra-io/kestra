import {beforeAll, describe, expect, it} from "vitest"
import type {HighlighterCore, ThemedToken} from "shiki/core"
import {kestraDark, kestraLight, pebbleBlocks, pebbleTransformer} from "./index.ts"
import {yamlCorpus} from "../tests/corpus.ts"
import {highlighter} from "../tests/highlighter.ts"

const PEBBLE = kestraDark.syntax.pebble
let h: HighlighterCore

beforeAll(async () => {
    h = await highlighter("javascript")
})

function colorsPerChar(code: string, tokens: ThemedToken[][]) {
    const colors = new Array<string | undefined>(code.length)
    for (const line of tokens) {
        for (const token of line) colors.fill(token.color?.toUpperCase(), token.offset, token.offset + token.content.length)
    }
    return colors
}

function transform(code: string, options: Parameters<HighlighterCore["codeToTokens"]>[1]) {
    const {tokens} = h.codeToTokens(code, options)
    return {before: tokens, after: pebbleTransformer().tokens.call({source: code, options} as never, tokens) as ThemedToken[][] | undefined}
}

describe("pebbleTransformer", () => {
    it.each(yamlCorpus().map(file => [file.path, file] as const))("paints exactly the Pebble blocks of %s", (_, {code}) => {
        const {before, after} = transform(code, {lang: "yaml", theme: "kestra-dark"})
        const blocks = pebbleBlocks(code)
        if (!blocks.length) return expect(after).toBe(before)

        const original = colorsPerChar(code, before)
        const painted = colorsPerChar(code, after!)
        const inBlock = new Uint8Array(code.length)
        for (const [start, end] of blocks) inBlock.fill(1, start, end)
        const wrong: string[] = []
        for (let i = 0; i < code.length && wrong.length < 3; i++) {
            const expected = inBlock[i] ? PEBBLE : original[i]
            if (painted[i] !== expected && !(/\s/.test(code[i]!) && painted[i] === undefined)) {
                wrong.push(`${i}: ${JSON.stringify(code.slice(i, i + 30))} is ${painted[i]}, expected ${expected}`)
            }
        }
        expect(wrong).toEqual([])
        expect(after!.flat().map(t => t.content).join("")).toBe(before.flat().map(t => t.content).join(""))
    })

    it("writes the CSS variables Shiki uses for dual themes", () => {
        const code = "message: \"Hi {{ inputs.name }}\""
        const pebbleSpan = (html: string) => html.match(/<span style="([^"]*)">\{\{ inputs\.name \}\}<\/span>/)?.[1]
        const themes = {light: "kestra-light", dark: "kestra-dark"}
        const transformers = [pebbleTransformer()]

        expect(pebbleSpan(h.codeToHtml(code, {lang: "yaml", themes, transformers})))
            .toBe(`color:${kestraLight.syntax.pebble};--shiki-dark:${PEBBLE}`)
        expect(pebbleSpan(h.codeToHtml(code, {lang: "yaml", themes, transformers, defaultColor: false})))
            .toBe(`--shiki-light:${kestraLight.syntax.pebble};--shiki-dark:${PEBBLE}`)
        expect(pebbleSpan(h.codeToHtml(code, {lang: "yaml", themes, transformers, defaultColor: "light-dark()"})))
            .toBe(`color:light-dark(${kestraLight.syntax.pebble}, ${PEBBLE});--shiki-light:${kestraLight.syntax.pebble};--shiki-dark:${PEBBLE}`)
        expect(pebbleSpan(h.codeToHtml(code, {lang: "yml", theme: "kestra-dark", transformers}))).toBe(`color:${PEBBLE}`)
    })

    it("leaves other languages and non-Kestra themes alone", () => {
        expect(transform("x = '{{ a }}'", {lang: "python", theme: "kestra-dark"}).after).toBeUndefined()
        expect(transform("x: '{{ a }}'", {lang: "yaml", theme: "probe"}).after).toBeUndefined()
        expect(transform("x: '{{ a }}'", {lang: "yaml", themes: {light: "probe", dark: "kestra-dark"}}).after).toBeUndefined()
    })
})
