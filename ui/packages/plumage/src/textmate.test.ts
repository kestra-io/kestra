import {beforeAll, describe, expect, it} from "vitest"
import type {HighlighterCore} from "shiki/core"
import {pebbleBlocks} from "./index.ts"
import type {SyntaxRole} from "./index.ts"
import {languageCorpus, yamlCorpus} from "../tests/corpus.ts"
import {highlighter} from "../tests/highlighter.ts"
import {roleOf} from "../tests/probe.ts"

const STRUCTURAL = /^(source|text|meta|markup\.(list|bold|italic|paragraph))\./

function rolesPerChar(h: HighlighterCore, code: string, lang: string): (SyntaxRole | undefined)[] {
    const roles = new Array<SyntaxRole | undefined>(code.length)
    for (const line of h.codeToTokensBase(code, {lang, theme: "probe"})) {
        for (const token of line) {
            const role = roleOf(token.color)
            for (let i = 0; i < token.content.length; i++) roles[token.offset + i] = role
        }
    }
    return roles
}

function where(code: string, index: number) {
    const line = code.slice(0, index).split("\n").length
    return `line ${line}: ${code.slice(index, index + 50).replace(/\n/g, "⏎")}`
}

const yaml = yamlCorpus()
let js: HighlighterCore
let oniguruma: HighlighterCore

beforeAll(async () => {
    [js, oniguruma] = await Promise.all([highlighter("javascript", {pebble: true}), highlighter("oniguruma", {pebble: true})])
})

describe("VS Code Pebble grammar and theme coverage on YAML", () => {
    it.each(yaml.map(file => [file.path, file] as const))("%s", (_, {code}) => {
        const roles = rolesPerChar(js, code, "yaml")
        const failures: string[] = []
        const inPebble = new Uint8Array(code.length)

        for (const [start, end] of pebbleBlocks(code)) {
            if (roles[start] === "comment") continue
            inPebble.fill(1, start, end)
            for (let i = start; i < end; i++) {
                if (!/\s/.test(code[i]!) && roles[i] !== "pebble") {
                    failures.push(`expected pebble, got ${roles[i]} at ${where(code, i)}`)
                    break
                }
            }
        }
        for (let i = 0; i < code.length; i++) {
            if (roles[i] === "pebble" && !inPebble[i]) {
                failures.push(`pebble outside a block at ${where(code, i)}`)
                break
            }
        }
        expect(failures.slice(0, 5)).toEqual([])
        expect(unstyledScopes(js, [{path: "", code}], "yaml")).toEqual({})
    })

    it("tokenizes identically on the JavaScript and Oniguruma regex engines", () => {
        const differing = yaml.filter(({code}) =>
            JSON.stringify(rolesPerChar(js, code, "yaml")) !== JSON.stringify(rolesPerChar(oniguruma, code, "yaml")))
        expect(differing.map(file => file.path)).toEqual([])
    })
})

describe("Shiki docs languages", () => {
    it.each([...languageCorpus()].map(([lang, files]) => [lang, files] as const))("%s leaves no meaningful scope unstyled", (lang, files) => {
        expect(unstyledScopes(js, files, lang)).toEqual({})
    })
})

/** Scope chains of non-blank tokens that fell through to the default color although a grammar gave them a meaning. */
function unstyledScopes(h: HighlighterCore, files: {path: string, code: string}[], lang: string) {
    const unstyled: Record<string, string> = {}
    for (const {path, code} of files) {
        for (const line of h.codeToTokensBase(code, {lang, theme: "probe", includeExplanation: "scopeName"})) {
            for (const token of line) {
                if (roleOf(token.color) !== "text" || !token.content.trim()) continue
                for (const part of token.explanation ?? []) {
                    const scopes = part.scopes.map(s => s.scopeName).filter(s => !STRUCTURAL.test(s))
                    if (scopes.length) unstyled[scopes.join(" > ")] = `${path}: ${part.content.trim().slice(0, 60)}`
                }
            }
        }
    }
    return unstyled
}
