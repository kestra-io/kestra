import {parse} from "@vue/compiler-sfc"
import {describe, expect, it} from "vitest"
import {compare, countByFile, countTemplateAny, merge} from "./baseline.mjs"

const anyAt = (filename) => ({code: "typescript(no-explicit-any)", filename})
const inTemplate = (template) => countTemplateAny(template, parse)

describe("countByFile", () => {
    it("counts only the explicit-any rule and normalises path separators", () => {
        const counts = countByFile([
            anyAt("src/a.ts"),
            anyAt("src\\a.ts"),
            {code: "eslint(no-shadow)", filename: "src/a.ts"},
            anyAt("src/b.vue"),
        ])
        expect(counts).toEqual({"src/a.ts": 2, "src/b.vue": 1})
    })

    it("orders keys by code point so two machines write the same file", () => {
        const counts = countByFile([anyAt("src/b.ts"), anyAt("src/B.ts"), anyAt("src/_a.ts")])
        expect(Object.keys(counts)).toEqual(["src/B.ts", "src/_a.ts", "src/b.ts"])
    })
})

describe("countTemplateAny", () => {
    it("counts every spelling in interpolations and directive values", () => {
        expect(inTemplate("<template>{{ (row as any).id }}</template>")).toBe(1)
        expect(inTemplate("<template><Foo @click='(e: any) => go(e)' :list='items as any[]' /></template>")).toBe(2)
        expect(inTemplate("<template><Foo :x='v as Record<string, any>' /></template>")).toBe(1)
    })

    it("leaves prose and static attributes alone", () => {
        expect(inTemplate("<template><div title='pick any row'>delete any tag</div></template>")).toBe(0)
        expect(inTemplate("<template><div :title='$t(`remove any tag`)' /></template>")).toBe(0)
    })

    it("ignores the script block, which oxlint already counts", () => {
        expect(inTemplate("<script setup lang='ts'>const a: any = 1</script><template><div /></template>")).toBe(0)
    })
})

describe("merge", () => {
    it("adds the template counts onto the script counts and keeps code-point order", () => {
        const counts = merge({"src/b.vue": 2, "src/a.ts": 1}, {"src/b.vue": 3, "src/c.vue": 1})
        expect(counts).toEqual({"src/a.ts": 1, "src/b.vue": 5, "src/c.vue": 1})
        expect(Object.keys(counts)).toEqual(["src/a.ts", "src/b.vue", "src/c.vue"])
    })
})

describe("compare", () => {
    const baseline = {"src/a.ts": 2, "src/gone.ts": 1}

    it("is quiet when nothing moved", () => {
        expect(compare(baseline, {...baseline})).toEqual({added: [], removed: []})
    })

    it("reports a file that gained one, including a file new to the baseline", () => {
        const {added, removed} = compare(baseline, {"src/a.ts": 3, "src/gone.ts": 1, "src/new.ts": 1})
        expect(added).toEqual([
            {file: "src/a.ts", was: 2, now: 3},
            {file: "src/new.ts", was: 0, now: 1},
        ])
        expect(removed).toEqual([])
    })

    it("reports a file that lost one, including a file that dropped out entirely", () => {
        const {added, removed} = compare(baseline, {"src/a.ts": 1})
        expect(added).toEqual([])
        expect(removed).toEqual([
            {file: "src/a.ts", was: 2, now: 1},
            {file: "src/gone.ts", was: 1, now: 0},
        ])
    })
})
