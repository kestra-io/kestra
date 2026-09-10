import {describe, expect, it} from "vitest"
import {compare, countByFile} from "./baseline.mjs"

const anyAt = (filename) => ({code: "typescript(no-explicit-any)", filename})

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
