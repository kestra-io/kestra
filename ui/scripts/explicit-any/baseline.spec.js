import {spawnSync} from "node:child_process"
import {mkdirSync, mkdtempSync, rmSync, symlinkSync, writeFileSync} from "node:fs"
import {tmpdir} from "node:os"
import {join} from "node:path"
import {parse} from "@vue/compiler-sfc"
import {describe, expect, it} from "vitest"
import {countByFile, countTemplateAny, merge} from "./baseline.mjs"

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

describe("check:ts-any", () => {
    // Vitest runs from `ui/`, and `import.meta.url` is not a file URL under jsdom.
    const uiRoot = process.cwd()
    const script = join(uiRoot, "scripts/explicit-any/baseline.mjs")

    const run = (source) => {
        const dir = mkdtempSync(join(tmpdir(), "explicit-any-"))
        try {
            mkdirSync(join(dir, "src"))
            writeFileSync(join(dir, "package.json"), "{}\n")
            writeFileSync(join(dir, "src/probe.ts"), source)
            // oxlint and the Vue compiler resolve from the working directory, so the probe borrows the real node_modules.
            symlinkSync(join(uiRoot, "node_modules"), join(dir, "node_modules"))
            return spawnSync(process.execPath, [script, "src"], {cwd: dir, encoding: "utf8"})
        } finally {
            rmSync(dir, {recursive: true, force: true})
        }
    }

    it("passes when there is no explicit any", () => {
        expect(run("export const a: unknown = 1\n").status).toBe(0)
    })

    it("fails on a single explicit any and names the file", () => {
        const result = run("export const a: any = 1\n")
        expect(result.status).toBe(1)
        expect(result.stderr).toContain("src/probe.ts: 1")
    })
})
