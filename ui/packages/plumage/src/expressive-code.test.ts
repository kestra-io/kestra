import {beforeAll, describe, expect, it} from "vitest"
import {ExpressiveCode, ExpressiveCodeTheme} from "expressive-code"
import type {Element, Nodes} from "expressive-code/hast"
import {kestraDark, kestraLight, pebbleTokens, toTextMateTheme} from "./index.ts"
import {pluginPebble} from "./expressive-code.ts"
import {yamlCorpus} from "../tests/corpus.ts"

let ec: ExpressiveCode

beforeAll(() => {
    ec = new ExpressiveCode({
        themes: [kestraDark, kestraLight].map(theme => new ExpressiveCodeTheme(toTextMateTheme(theme))),
        plugins: [pluginPebble()],
    })
})

/** The block color of each variant: anything outside a block painted with it is a block that leaked. */
const PEBBLE_COLORS = new Set([kestraDark.syntax.pebble, kestraLight.syntax.pebble])

const isLine = (node: Element) => [node.properties?.className].flat().includes("ec-line")

/** The text and per-variant colors of every rendered line, read back from Expressive Code's output tree. */
function renderedLines(root: Nodes) {
    const lines: {text: string, colors: string[][]}[] = []
    const walk = (node: Nodes, colors: string[], line?: {text: string, colors: string[][]}) => {
        if (node.type === "text" && line) {
            line.text += node.value
            for (let i = 0; i < node.value.length; i++) line.colors.push(colors)
            return
        }
        if (node.type !== "element" && node.type !== "root") return
        const style = node.type === "element" ? String(node.properties?.style ?? "") : ""
        const own = [...style.matchAll(/--(\d+):([^;]+)/g)]
        const next = own.length ? own.map(match => match[2]!.toUpperCase()) : colors
        const current = node.type === "element" && isLine(node) ? {text: "", colors: []} : line
        if (current && current !== line) lines.push(current)
        node.children.forEach(child => walk(child, next, current))
    }
    walk(root, [])
    return lines.map(line => line.text === "\n" ? {text: "", colors: []} : line)
}

describe("pluginPebble", () => {
    it.each(yamlCorpus().map(file => [file.path, file] as const))("paints exactly the Pebble tokens of %s", async (_, {code: source}) => {
        const {renderedGroupAst} = await ec.render({code: source, language: "yaml"})
        const lines = renderedLines(renderedGroupAst)
        const code = lines.map(line => line.text).join("\n")

        const colors = lines.flatMap(line => [...line.colors, []])
        const wanted = new Array<string[] | undefined>(code.length)
        for (const [start, end, role] of pebbleTokens(code)) wanted.fill([kestraDark.syntax[role], kestraLight.syntax[role]], start, end)
        const wrong: string[] = []
        for (let i = 0; i < colors.length && wrong.length < 3; i++) {
            if (/\s/.test(code[i]!)) continue
            const painted = colors[i]!.length > 0 && colors[i]!.every(color => PEBBLE_COLORS.has(color))
            if (wanted[i] ? colors[i]![0] !== wanted[i]![0] || colors[i]![1] !== wanted[i]![1] : painted) {
                wrong.push(`${JSON.stringify(code.slice(i, i + 30))}: ${colors[i]}, wanted ${wanted[i] ?? "the YAML color"}`)
            }
        }
        expect(wrong).toEqual([])
    })

    it("leaves other languages untouched", async () => {
        const {renderedGroupAst} = await ec.render({code: "print('{{ a }}')", language: "python"})
        const [line] = renderedLines(renderedGroupAst)
        expect(line!.colors.some(colors => colors[0] === kestraDark.syntax.pebble)).toBe(false)
    })
})
