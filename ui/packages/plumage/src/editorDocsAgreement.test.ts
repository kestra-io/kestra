import {beforeAll, describe, expect, it} from "vitest"
import type {HighlighterCore} from "shiki/core"
import {pebbleBlocks} from "./index.ts"
import type {SyntaxRole} from "./index.ts"
import {yamlCorpus} from "../tests/corpus.ts"
import {highlighter} from "../tests/highlighter.ts"
import {ksEditorYamlRoles} from "../tests/kseditor.ts"
import {roleOf} from "../tests/probe.ts"

const corpus = yamlCorpus()
let shiki: HighlighterCore
let ksEditor: (code: string) => SyntaxRole[]

beforeAll(async () => {
    [shiki, ksEditor] = await Promise.all([highlighter("javascript", {langs: ["yaml"]}), ksEditorYamlRoles()])
})

function shikiRoles(code: string) {
    const roles = new Array<SyntaxRole | undefined>(code.length)
    for (const line of shiki.codeToTokensBase(code, {lang: "yaml", theme: "probe"})) {
        for (const token of line) roles.fill(roleOf(token.color), token.offset, token.offset + token.content.length)
    }
    return roles
}

describe("KsEditor and docs agreement on YAML", () => {
    it("gives the same role to almost every character", () => {
        const mismatches = new Map<string, {count: number, example: string}>()
        let compared = 0
        let agreed = 0
        for (const {path, code} of corpus) {
            const editor = ksEditor(code)
            const docs = shikiRoles(code)
            const skip = new Uint8Array(code.length)
            for (const [start, end] of pebbleBlocks(code)) skip.fill(1, start, end)
            for (let i = 0; i < code.length; i++) {
                if (skip[i] || /\s/.test(code[i]!)) continue
                compared++
                if (editor[i] === docs[i]) {
                    agreed++
                    continue
                }
                const key = `${editor[i]} → ${docs[i]}`
                const entry = mismatches.get(key) ?? {count: 0, example: `${path}: ${code.slice(code.lastIndexOf("\n", i) + 1, code.indexOf("\n", i)).trim().slice(0, 70)}`}
                entry.count++
                mismatches.set(key, entry)
            }
        }
        const top = [...mismatches].sort((a, b) => b[1].count - a[1].count).slice(0, 12)
        console.info(`agreement ${(agreed / compared * 100).toFixed(2)}% of ${compared} characters\n` + top.map(([key, {count, example}]) => `  ${key}: ${count}  e.g. ${example}`).join("\n"))
        expect(agreed / compared).toBeGreaterThan(0.99)
    }, 120_000)
})
