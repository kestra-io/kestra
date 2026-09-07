// The fixtures below are CSS written as strings, so the rule under test would report itself.
/* eslint-disable kestra-tokens/no-undeclared-ks-token */
import {execFileSync} from "node:child_process"
import {describe, expect, it} from "vitest"
import {RuleTester} from "eslint"
// @ts-expect-error - plain .mjs modules, so that both linters and this test load the same code
import {declarationsIn, knownTokens, suggest, usagesIn} from "../../../scripts/tokens/knownTokens.mjs"
// @ts-expect-error - see above
import eslintPlugin from "../../../scripts/tokens/eslintPlugin.mjs"

interface Warning {rule: string; severity: string; text: string}

/**
 * Stylelint runs in its own process: it loads the real config, plugin and palette, which is the path
 * CI and an editor take, and none of that survives being imported into the jsdom test environment.
 */
// A clean file prints nothing at all, not an empty report.
const report = (output: string): Warning[] => output.includes("[")
    ? JSON.parse(output.slice(output.indexOf("["), output.lastIndexOf("]") + 1))[0].warnings
    : []

const lintTolerant = (code: string, file = "probe.scss"): Warning[] => {
    const run = () => execFileSync(
        process.execPath,
        ["node_modules/stylelint/bin/stylelint.mjs", "--stdin", `--stdin-filename=${file}`, "--formatter", "json"],
        {input: code, encoding: "utf-8", cwd: process.cwd(), stdio: ["pipe", "pipe", "pipe"]},
    )
    try {
        return report(run())
    } catch (error) {
        // A file carrying an error exits non-zero; stylelint still wrote the report first.
        const {stdout, stderr} = error as {stdout?: string; stderr?: string}
        return report(`${stdout ?? ""}${stderr ?? ""}`)
    }
}

const warningsFor = (code: string, file = "probe.scss") => lintTolerant(code, file).map(warning => warning.text)

describe("the known-token set", () => {
    it("reads the names the Figma palette generator writes", () => {
        expect(knownTokens().has("--ks-text-link")).toBe(true)
    })

    it.each([
        ["plain CSS", "--ks-local: red;"],
        ["scss interpolation", "#{--ks-local}: red;"],
        ["a quoted key in a style object", "const s = {'--ks-local': '1px'}"],
        ["a runtime setProperty", "el.style.setProperty(\"--ks-local\", v)"],
    ])("counts a declaration written as %s", (_label, source) => {
        expect(declarationsIn(source)).toContain("--ks-local")
    })

    it("leaves out a name built by interpolation, since it is only known at runtime", () => {
        expect([...usagesIn("color: var(--ks-status-#{$state});")]).toEqual([])
    })

    it("proposes the recorded replacement for a retired name rather than the nearest string", () => {
        expect(suggest("--ks-primary", knownTokens())).toEqual(["--ks-text-link", "--ks-border-focus"])
    })
})

describe("the stylelint rule, ks/no-undeclared-custom-property", () => {
    it("reports a token that nothing declares", () => {
        expect(warningsFor(".x { color: var(--ks-nowhere); }"))
            .toEqual([expect.stringContaining("\"--ks-nowhere\" is not declared")])
    })

    it("accepts a declared token", () => {
        expect(warningsFor(".x { color: var(--ks-text-link); }")).toEqual([])
    })

    it("fails the build for an undeclared token while the rest of the config only warns", () => {
        const reported = lintTolerant(".x { color: var(--ks-nowhere); border-color: #fff; }")
        expect(reported.map(warning => [warning.rule, warning.severity])).toEqual([
            ["ks/no-undeclared-custom-property", "error"],
            ["color-no-hex", "warning"],
        ])
    })

    it("accepts a token the file under lint declares itself, even when it is unsaved", () => {
        expect(warningsFor(".x { --ks-just-typed: red; color: var(--ks-just-typed); }")).toEqual([])
    })

    it("reads the style block of a single-file component", () => {
        const sfc = "<template><i /></template>\n<style scoped>\n.x { color: var(--ks-nowhere); }\n</style>\n"
        expect(warningsFor(sfc, "probe.vue"))
            .toEqual([expect.stringContaining("\"--ks-nowhere\" is not declared")])
    })
})

describe("the eslint rule", () => {
    it("covers the tokens stylelint cannot see", () => {
        const tester = new RuleTester({languageOptions: {ecmaVersion: 2022, sourceType: "module"}})

        expect(() => tester.run("no-undeclared-ks-token", eslintPlugin.rules["no-undeclared-ks-token"], {
            valid: [
                {code: "cssVar(\"--ks-text-link\")"},
                {code: "const css = \"color: var(--ks-text-link)\""},
                // A name this file declares itself, which is why it appears in no stylesheet.
                {code: "el.style.setProperty(\"--ks-font-scale\", scale)"},
            ],
            invalid: [
                {code: "cssVar(\"--ks-nowhere\")", errors: [{messageId: "undeclared"}]},
                {code: "const css = \"color: var(--ks-nowhere)\"", errors: [{messageId: "undeclared"}]},
                {code: "const css = `border: 1px solid var(--ks-nowhere)`", errors: [{messageId: "undeclared"}]},
            ],
        })).not.toThrow()
    })
})
