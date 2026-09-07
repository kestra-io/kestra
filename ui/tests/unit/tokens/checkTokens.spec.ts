import {mkdtempSync, writeFileSync} from "node:fs"
import {tmpdir} from "node:os"
import {join} from "node:path"
import {beforeEach, describe, expect, it} from "vitest"
import {checkTokens} from "../../../scripts/tokens/checkTokens.ts"

describe("checkTokens", () => {
    let root: string
    let palette: string

    const write = (name: string, source: string) => writeFileSync(join(root, name), source)
    const run = () => checkTokens([root], palette)

    beforeEach(() => {
        root = mkdtempSync(join(tmpdir(), "kestra-tokens-"))
        palette = join(root, "palette.json")
        writeFileSync(palette, JSON.stringify(["--ks-bg-base", "--ks-text-link", "--ks-border-default"]))
    })

    it("flags a token that nothing declares", () => {
        write("a.scss", ".x { color: var(--ks-nowhere); }")

        expect(run().dead).toMatchObject([{token: "--ks-nowhere", line: 1}])
    })

    it("accepts a token the Figma palette declares", () => {
        write("a.scss", ".x { color: var(--ks-text-link); }")

        expect(run().dead).toEqual([])
    })

    it.each([
        ["scss interpolation", "styles.scss", "#{--ks-local}: red;\n.x { color: var(--ks-local); }"],
        ["a quoted key in a style object", "Card.vue", "const s = {'--ks-local': '1px'}\n// var(--ks-local)\n.x { width: var(--ks-local); }"],
        ["a runtime setProperty", "boot.ts", "el.style.setProperty(\"--ks-local\", v)\n// .x { color: var(--ks-local) }"],
    ])("accepts a token declared by %s", (_label, file, source) => {
        write(file, source)

        expect(run().dead).toEqual([])
    })

    it("skips a name that is built by interpolation, since it is only known at runtime", () => {
        write("a.scss", ".x { color: var(--ks-status-#{$state}); }")

        expect(run().dead).toEqual([])
    })

    it("separates a usage carrying a fallback, which still renders", () => {
        write("a.scss", ".x { color: var(--ks-nowhere, var(--ks-text-link)); }")

        const report = run()
        expect(report.dead).toEqual([])
        expect(report.withFallback).toMatchObject([{token: "--ks-nowhere"}])
    })

    it("proposes the recorded replacement for a retired name rather than the nearest string", () => {
        write("a.scss", ".x { color: var(--ks-primary); }")

        expect(run().dead[0].suggestions).toEqual(["--ks-text-link"])
    })
})
