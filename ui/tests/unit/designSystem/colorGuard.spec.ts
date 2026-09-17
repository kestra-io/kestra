import {describe, expect, it} from "vitest"
import {mkdtempSync, writeFileSync} from "node:fs"
import {tmpdir} from "node:os"
import {dirname, join, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {findHardcodedColors} from "./colorGuard"

const UI = resolve(dirname(fileURLToPath(import.meta.url)), "../../..")

const scan = (styles: string): string[] => {
    const dir = mkdtempSync(join(tmpdir(), "color-guard-"))
    writeFileSync(join(dir, "Component.vue"), `<template><div /></template>\n<style scoped lang="scss">\n${styles}\n</style>\n`)
    return findHardcodedColors(dir).map((offence) => offence.replace("Component.vue:", "line "))
}

const scanScript = (script: string): string[] => {
    const dir = mkdtempSync(join(tmpdir(), "color-guard-"))
    writeFileSync(join(dir, "module.ts"), script)
    return findHardcodedColors(dir).map((offence) => offence.replace("module.ts:", "line "))
}

describe("design system", () => {
    it.each(["src", "packages/topology/src"])("takes every colour from a --ks-* token across ui/%s", (root) => {
        const offenders = findHardcodedColors(resolve(UI, root))
        expect(
            offenders,
            `Replace the hardcoded colour with a --ks-* token (see ui/AGENTS.md):\n${offenders.join("\n")}`,
        ).toEqual([])
    })

    it("reports a hex, an rgb and a keyword colour, one entry per line", () => {
        expect(scan(".a {\n    color: #ff0000;\n}\n.b {\n    background: rgba(0, 0, 0, 0.5);\n}\n.c {\n    border-color: hotpink;\n}")).toEqual([
            "line 4 (#ff0000)",
            "line 7 (rgba()",
            "line 10 (hotpink)",
        ])
        expect(scan(".a {\n    border-color: rebeccapurple;\n}")).toEqual(["line 4 (rebeccapurple)"])
    })

    it("reports a colour written with hsl, hwb, lab, lch or oklch", () => {
        expect(scan(".a {\n    color: hsl(120deg 50% 50%);\n    background: oklch(0.7 0.1 200);\n    border-color: lab(50% 40 60);\n}")).toEqual([
            "line 4 (hsl()",
            "line 5 (oklch()",
            "line 6 (lab()",
        ])
        expect(scan(".a {\n    color: hsl(var(--hue) 50% 50%);\n}")).toEqual([])
    })

    it("reads a custom property named like a colour, a shorthand, and a colour used to lighten a token", () => {
        expect(scan(".a {\n    --brand-color: red;\n}")).toEqual(["line 4 (red)"])
        expect(scan(".a {\n    text-decoration: underline crimson;\n}")).toEqual(["line 4 (crimson)"])
        expect(scan(".a {\n    --tint: color-mix(in srgb, var(--ks-text-link) 70%, white 30%);\n}")).toEqual([
            "line 4 (white)",
        ])
    })

    it("reports a hex assigned to an SCSS variable, before it reaches any property", () => {
        expect(scan("$node: #7081b9;\n.a {\n    --node: #{$node};\n}")).toEqual(["line 3 (#7081b9)"])
    })

    it("reports a hex in a script object under a camelCase colour key", () => {
        expect(scanScript("const TYPE = {\n    WORKER: {colorHex: \"#9F9DFF\"},\n    STYLE: {backgroundColor: \"#C182FF\"},\n}\n")).toEqual([
            "line 2 (#9F9DFF)",
            "line 3 (#C182FF)",
        ])
    })

    it("reports an Element Plus or Bootstrap variable and an SCSS colour variable", () => {
        expect(scan(".a {\n    color: var(--bs-gray-900);\n}")).toEqual(["line 4 (var(--bs-gray-900)"])
        expect(scan(".a {\n    border-color: var(--el-color-primary);\n}")).toEqual(["line 4 (var(--el-color-primary)"])
        expect(scan(".a {\n    color: var(--kel-text-color-regular);\n}")).toEqual(["line 4 (var(--kel-text-color-regular)"])
        expect(scan(".a {\n    color: $brand;\n}")).toEqual(["line 4 ($brand)"])
    })

    it("leaves a wrapped variable that carries no colour alone", () => {
        expect(scan(".a {\n    font-size: var(--kel-font-size-small);\n    border-radius: var(--kel-border-radius-base);\n}")).toEqual([])
    })

    it("leaves an SCSS variable that only aliases a token alone", () => {
        expect(scan("$muted: var(--ks-text-secondary);\n.a {\n    color: $muted;\n}")).toEqual([])
    })

    it("does not read a custom property whose name says it holds no colour", () => {
        expect(scan(".a {\n    --font-weight: normal;\n    --easing: ease-in-out;\n    --repeat: infinite;\n    --size: 12px;\n}")).toEqual([])
    })

    it("reads a colour inside a gradient, including one that spans lines", () => {
        expect(scan(".a {\n    background: linear-gradient(\n        red,\n        var(--ks-bg-base)\n    );\n}")).toEqual([
            "line 4 (red)",
        ])
    })

    it("leaves tokens, keywords and text that only looks like a colour alone", () => {
        expect(scan([
            ".a {",
            "    color: var(--ks-text-primary);",
            "    background: var(--ks-white);",
            "    border: 1px solid var(--ks-border-default);",
            "    background: url(tan.png) no-repeat center;",
            "    box-shadow: inset 0 0 0 1px var(--ks-border-focus);",
            "    background: color-mix(in srgb, var(--ks-status-info) 20%, transparent);",
            "    content: \"red\";",
            "    font-weight: bold;",
            "}",
        ].join("\n"))).toEqual([])
    })

    it("counts lines past a block comment, and never reads the comment itself", () => {
        expect(scan("/* the old value was\n   color: red\n   across two lines */\n.a {\n    color: hotpink;\n}")).toEqual([
            "line 7 (hotpink)",
        ])
    })

    it("honours an opt-out on the next line only", () => {
        expect(scan(".a {\n    /* design-system-disable-next-line */\n    color: red;\n    background: hotpink;\n}")).toEqual([
            "line 6 (hotpink)",
        ])
    })

    it("has no whole-file opt-out", () => {
        expect(scan("/* design-system-disable: fixture */\n.a {\n    color: red;\n}")).toEqual(["line 5 (red)"])
    })

    it("honours an opt-out over a run of lines, and guards again after it ends", () => {
        expect(scan([
            "/* design-system-disable-start: fixture */",
            ".a { color: red; }",
            ".b { color: crimson; }",
            "/* design-system-disable-end */",
            ".c { color: hotpink; }",
        ].join("\n"))).toEqual(["line 7 (hotpink)"])
    })

    it("reports a start with no end rather than muting the rest of the file", () => {
        expect(scan([
            "/* design-system-disable-start: fixture */",
            ".a { color: red; }",
            ".b { color: crimson; }",
        ].join("\n"))).toEqual(["line 3 (design-system-disable-start without design-system-disable-end)"])
    })
})
