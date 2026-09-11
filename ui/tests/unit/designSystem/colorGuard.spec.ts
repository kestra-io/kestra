import {describe, expect, it} from "vitest"
import {mkdtempSync, writeFileSync} from "node:fs"
import {tmpdir} from "node:os"
import {dirname, join, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {findHardcodedColors} from "./colorGuard"

const SRC = resolve(dirname(fileURLToPath(import.meta.url)), "../../../src")

const scan = (styles: string): string[] => {
    const dir = mkdtempSync(join(tmpdir(), "color-guard-"))
    writeFileSync(join(dir, "Component.vue"), `<template><div /></template>\n<style scoped lang="scss">\n${styles}\n</style>\n`)
    return findHardcodedColors(dir).map((offence) => offence.replace("Component.vue:", "line "))
}

describe("design system", () => {
    it("takes every colour from a --ks-* token across ui/src", () => {
        const offenders = findHardcodedColors(SRC)
        expect(
            offenders,
            `Replace the hardcoded colour with a --ks-* token (see ui/AGENTS.md):\n${offenders.join("\n")}`,
        ).toEqual([])
    })

    it("reports a hex, an rgb and a keyword colour, as one entry per file", () => {
        expect(scan(".a {\n    color: #ff0000;\n}\n.b {\n    background: rgba(0, 0, 0, 0.5);\n}\n.c {\n    border-color: hotpink;\n}")).toEqual([
            "line 4 (#ff0000, rgba(, hotpink)",
        ])
        expect(scan(".a {\n    border-color: rebeccapurple;\n}")).toEqual(["line 4 (rebeccapurple)"])
    })

    it("reads a custom property named like a colour, a shorthand, and a colour used to lighten a token", () => {
        expect(scan(".a {\n    --brand-color: red;\n}")).toEqual(["line 4 (red)"])
        expect(scan(".a {\n    text-decoration: underline crimson;\n}")).toEqual(["line 4 (crimson)"])
        expect(scan(".a {\n    --tint: color-mix(in srgb, var(--ks-text-link) 70%, white 30%);\n}")).toEqual([
            "line 4 (white)",
        ])
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

    it("honours an opt-out on the next line and for a whole file", () => {
        expect(scan(".a {\n    /* design-system-disable-next-line */\n    color: red;\n    background: hotpink;\n}")).toEqual([
            "line 6 (hotpink)",
        ])
        expect(scan("/* design-system-disable: fixture */\n.a {\n    color: red;\n}")).toEqual([])
    })
})
