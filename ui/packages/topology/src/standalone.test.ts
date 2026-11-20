import fs from "node:fs"
import path from "node:path"
import {describe, expect, it} from "vitest"

const script = path.resolve(__dirname, "../scripts/generate-standalone-css.mjs")
const committed = fs.readFileSync(path.resolve(__dirname, "standalone.css"), "utf8")

describe("standalone.css", () => {
    it("matches the design system it is generated from", async () => {
        const {buildStandaloneCss} = (await import(/* @vite-ignore */ script)) as {buildStandaloneCss: () => string}

        // On failure: `npm run generate:standalone --workspace=@kestra-io/topology`
        expect(buildStandaloneCss()).toBe(committed)
    })

    it("declares the tokens on the graph only, never on the page", () => {
        const tokenBlocks = committed.slice(0, committed.indexOf("/* Element Plus base"))

        expect(tokenBlocks).not.toMatch(/^:root\b|^html\.dark/m)
        expect(tokenBlocks).toMatch(/^:where\(\.ks-topology, \.kel-popper\) \{/m)
        expect(tokenBlocks).toMatch(/^:where\(html\.dark\) :where\(\.ks-topology, \.kel-popper\) \{/m)
    })

    it("leaves the font scale to the host", () => {
        expect(committed).not.toMatch(/--ks-font-scale\s*:/)
        expect(committed).toContain("var(--ks-font-scale, 1)")
    })
})
