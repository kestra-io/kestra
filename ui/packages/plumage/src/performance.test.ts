import {beforeAll, describe, expect, it} from "vitest"
import {ExpressiveCode, ExpressiveCodeBlock, ExpressiveCodeTheme, InlineStyleAnnotation, loadShikiTheme, type ExpressiveCodeHook} from "expressive-code"
import type {HighlighterCore, ThemedToken} from "shiki/core"
import {kestraDark, kestraLight, pebbleBlocks, pebbleTransformer, toTextMateTheme} from "./index.ts"
import {pluginPebble} from "./expressive-code.ts"
import {yamlCorpus} from "../tests/corpus.ts"
import {highlighter} from "../tests/highlighter.ts"

const corpus = yamlCorpus().map(file => file.code)
const kb = (corpus.reduce((sum, code) => sum + code.length, 0) / 1024).toFixed(0)

/** Fastest of several runs after a warm-up, so one GC pause does not decide the result. */
async function fastest(run: () => unknown, runs = 3) {
    await run()
    let best = Infinity
    for (let i = 0; i < runs; i++) {
        const start = performance.now()
        await run()
        best = Math.min(best, performance.now() - start)
    }
    return best
}

/** Best time of each run after a warm-up, alternating them every round so drift and CPU contention hit all of them alike. */
async function compare(...runs: (() => unknown)[]) {
    for (const run of runs) await run()
    const best = runs.map(() => Infinity)
    for (let round = 0; round < 7; round++) {
        for (const [i, run] of runs.entries()) {
            const start = performance.now()
            await run()
            best[i] = Math.min(best[i]!, performance.now() - start)
        }
    }
    return best
}

const PATHOLOGICAL: [string, string][] = [
    ["50k unclosed {{ on one line", "message: " + "{{ a ".repeat(50_000)],
    ["20k lines each opening {{", "tasks:\n" + "  - message: \"{{ never closed\n".repeat(20_000)],
    ["mixed openers without closers", "x: " + "{%{{{%s{%-".repeat(20_000)],
    ["closers without openers", "x: " + "}}%}".repeat(50_000)],
    ["200k character line ending in Pebble", "x: " + "a".repeat(200_000) + " {{ b }}"],
    ["10k Pebble blocks on one line", "x: " + "{{ a }} ".repeat(10_000)],
]

describe("build cost in Expressive Code", () => {
    it("spends almost none of the render time in the Pebble hook", async () => {
        const plugin = pluginPebble()
        const hook = plugin.hooks!.postprocessAnalyzedCode!
        let inHook = 0
        plugin.hooks!.postprocessAnalyzedCode = async context => {
            const start = performance.now()
            await hook(context)
            inHook += performance.now() - start
        }
        const ec = new ExpressiveCode({themes: [kestraDark, kestraLight].map(theme => new ExpressiveCodeTheme(toTextMateTheme(theme))), plugins: [plugin]})
        for (const code of corpus) await ec.render({code, language: "yaml"})
        inHook = 0
        const start = performance.now()
        for (const code of corpus) await ec.render({code, language: "yaml"})
        const total = performance.now() - start
        console.info(`pluginPebble: ${inHook.toFixed(1)} ms of ${total.toFixed(0)} ms rendering ${corpus.length} YAML blocks (${kb} KB)`)
        expect(inHook / total).toBeLessThan(0.05)
    }, 300_000)

    it("keeps end-to-end rendering close to the themes alone", async () => {
        const render = (plugins: ReturnType<typeof pluginPebble>[], themes: ExpressiveCodeTheme[]) => {
            const ec = new ExpressiveCode({themes, plugins})
            return async () => {
                for (const code of corpus) await ec.render({code, language: "yaml"})
            }
        }
        const kestra = [kestraDark, kestraLight].map(theme => new ExpressiveCodeTheme(toTextMateTheme(theme)))
        const github = await Promise.all(["github-dark-default", "github-light-default"].map(async name => new ExpressiveCodeTheme(await loadShikiTheme(name as Parameters<typeof loadShikiTheme>[0]))))

        const [today, themesOnly, withPebble] = await compare(render([], github), render([], kestra), render([pluginPebble()], kestra)) as [number, number, number]
        console.info(`Expressive Code, ${corpus.length} YAML blocks (${kb} KB): GitHub themes ${today.toFixed(0)} ms, Kestra themes ${themesOnly.toFixed(0)} ms, Kestra themes + Pebble ${withPebble.toFixed(0)} ms`)
        expect(withPebble / themesOnly).toBeLessThan(1.15)
    }, 300_000)

    it("recolors 10k Pebble blocks on one line in linear time", () => {
        const paint = (blocks: number) => {
            const code = "x: " + "{{ a }} ".repeat(blocks)
            const codeBlock = new ExpressiveCodeBlock({code, language: "yaml"})
            const line = codeBlock.getLines()[0]!
            for (let column = 0; column < code.length; column += 4) {
                for (const styleVariantIndex of [0, 1]) {
                    const inlineRange = {columnStart: column, columnEnd: Math.min(column + 4, code.length)}
                    line.addAnnotation(new InlineStyleAnnotation({color: "#FFFFFF", styleVariantIndex, renderPhase: "earliest", inlineRange}))
                }
            }
            const context = {codeBlock, styleVariants: [kestraDark, kestraLight].map(theme => ({theme: {name: theme.name}}))}
            const start = performance.now()
            pluginPebble().hooks!.postprocessAnalyzedCode!(context as unknown as Parameters<ExpressiveCodeHook>[0])
            return performance.now() - start
        }
        const single = paint(10_000)
        const doubled = paint(20_000)
        console.info(`pluginPebble: 10k blocks on one line in ${single.toFixed(0)} ms, 20k in ${doubled.toFixed(0)} ms`)
        expect(single).toBeLessThan(200)
        expect(doubled).toBeLessThan(single * 4)
    })
})

describe("pebbleTransformer", () => {
    let h: HighlighterCore

    beforeAll(async () => {
        h = await highlighter("oniguruma", {langs: ["yaml"]})
    })

    const run = (code: string, tokens: ThemedToken[][]) =>
        pebbleTransformer().tokens.call({source: code, options: {lang: "yaml", theme: "kestra-dark"}}, tokens)

    it("costs a small fraction of tokenization", async () => {
        const tokenized = corpus.map(code => h.codeToTokensBase(code, {lang: "yaml", theme: "kestra-dark"}))
        const tokenize = await fastest(() => corpus.forEach(code => h.codeToTokensBase(code, {lang: "yaml", theme: "kestra-dark"})))
        const paint = await fastest(() => corpus.forEach((code, i) => run(code, tokenized[i]!)))
        console.info(`pebbleTransformer: ${paint.toFixed(1)} ms on top of ${tokenize.toFixed(0)} ms of tokenization (${kb} KB)`)
        expect(paint / tokenize).toBeLessThan(0.05)
    }, 120_000)

    it.each(PATHOLOGICAL)("paints %s in bounded time", async (_, code) => {
        const tokens = h.codeToTokensBase(code, {lang: "yaml", theme: "kestra-dark"})
        const ms = await fastest(() => run(code, tokens))
        expect(ms).toBeLessThan(50)
    }, 60_000)
})

describe("pebbleBlocks", () => {
    it.each(PATHOLOGICAL)("scans %s in linear time", async (_, code) => {
        const ms = await fastest(() => pebbleBlocks(code))
        const doubled = await fastest(() => pebbleBlocks(code + "\n" + code))
        expect(ms).toBeLessThan(20)
        expect(doubled).toBeLessThan(Math.max(ms * 4, 2))
    })

    it("scans the whole corpus fast", async () => {
        const ms = await fastest(() => corpus.forEach(pebbleBlocks))
        console.info(`pebbleBlocks: ${kb} KB in ${ms.toFixed(1)} ms`)
        expect(ms).toBeLessThan(100)
    })
})
