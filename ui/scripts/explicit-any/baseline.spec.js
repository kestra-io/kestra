import {parse} from "@vue/compiler-sfc"
import {describe, expect, it} from "vitest"
import {compare, countByFile, countTemplateAny, decide, merge} from "./baseline.mjs"

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
        expect(inTemplate("<template><Foo :x='v as Map<any, Map<any, any>>' /></template>")).toBe(3)
    })

    it("reads code, not the text around it", () => {
        expect(inTemplate("<template><div title='pick any row'>delete any tag</div></template>")).toBe(0)
        expect(inTemplate("<template>{{ t('accepts: any value') }}</template>")).toBe(0)
        expect(inTemplate("<template>{{ `an ${row.kind as any} thing` }}</template>")).toBe(1)
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

    it("follows a rename git reports, whether the file kept its count or improved, but not one that gained an any", () => {
        const renames = {"src/New.vue": "src/Old.vue"}
        expect(compare({"src/Old.vue": 2}, {"src/New.vue": 2}, renames)).toMatchObject({added: [], removed: [], moved: [{file: "src/New.vue", from: "src/Old.vue", was: 2, now: 2}]})
        expect(compare({"src/Old.vue": 2}, {"src/New.vue": 1}, renames).moved).toEqual([{file: "src/New.vue", from: "src/Old.vue", was: 2, now: 1}])
        expect(compare({"src/Old.vue": 2}, {"src/New.vue": 3}, renames).added).toEqual([{file: "src/New.vue", was: 0, now: 3}])
    })

    it("does not pair a deleted file with an unrelated new one just because the counts match", () => {
        const {added, removed, moved} = compare({"src/Old.vue": 2}, {"src/BrandNew.vue": 2})
        expect(moved).toEqual([])
        expect(added).toEqual([{file: "src/BrandNew.vue", was: 0, now: 2}])
        expect(removed).toEqual([{file: "src/Old.vue", was: 2, now: 0}])
    })
})

describe("decide", () => {
    const added = [{file: "src/a.ts", was: 0, now: 1}]
    const removed = [{file: "src/b.ts", was: 2, now: 1}]

    it("refuses a new any until both --write and --accept-new-any are given", () => {
        expect(decide({added, removed: [], write: false, acceptNewAny: false}).action).toBe("fail")
        expect(decide({added, removed: [], write: true, acceptNewAny: false})).toMatchObject({action: "fail", reason: "added"})
        expect(decide({added, removed: [], write: false, acceptNewAny: true}).action).toBe("fail")
        expect(decide({added, removed: [], write: true, acceptNewAny: true})).toMatchObject({action: "write", raised: true})
    })

    it("asks for --write when the baseline is stale, and updates once it is given", () => {
        const moved = [{file: "src/new.vue", from: "src/old.vue", was: 2, now: 2}]
        expect(decide({added: [], removed, write: false, acceptNewAny: false})).toMatchObject({action: "fail", reason: "stale"})
        expect(decide({added: [], removed, write: true, acceptNewAny: false})).toMatchObject({action: "write", raised: false})
        expect(decide({added: [], removed: [], moved, write: false, acceptNewAny: false})).toMatchObject({action: "fail", reason: "stale"})
        expect(decide({added: [], removed: [], moved, write: true, acceptNewAny: false})).toMatchObject({action: "write", raised: false})
    })

    it("does nothing when the counts match", () => {
        expect(decide({added: [], removed: [], write: true, acceptNewAny: false}).action).toBe("ok")
    })
})
