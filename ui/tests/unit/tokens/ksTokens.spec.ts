// The fixtures below are CSS written as strings, so the rule under test would report itself.
/* eslint-disable kestra-tokens/no-undeclared-ks-token */
import {spawnSync} from "node:child_process"
import {describe, expect, it} from "vitest"
import {ESLint, RuleTester} from "eslint"
import css from "@eslint/css"
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
    // Both streams: stylelint writes its report to stderr, and exits non-zero for a file carrying an
    // error, so reading stdout alone would silently return an empty report for every case.
    const {stdout, stderr} = spawnSync(
        process.execPath,
        ["node_modules/stylelint/bin/stylelint.mjs", "--stdin", `--stdin-filename=${file}`, "--formatter", "json"],
        {input: code, encoding: "utf-8", cwd: process.cwd()},
    )
    return report(`${stdout ?? ""}${stderr ?? ""}`)
}

const warningsFor = (code: string, file = "probe.scss") => lintTolerant(code, file).map(warning => warning.text)

describe("the known-token set", () => {
    it("reads the names the Figma palette generator writes", () => {
        expect(knownTokens().has("--ks-text-link")).toBe(true)
    })

    it.each([
        ["plain CSS", "--ks-local: red;", "--ks-local"],
        ["scss interpolation", "#{--ks-local}: red;", "--ks-local"],
        ["a quoted key in a style object", "const s = {'--ks-local': '1px'}", "--ks-local"],
        ["a runtime setProperty", "el.style.setProperty(\"--ks-local\", v)", "--ks-local"],
        ["an @property registration", "@property --ks-local { syntax: \"<length>\" }", "--ks-local"],
        ["an @property registration in mixed case", "@property --ks-Local { syntax: \"<length>\" }", "--ks-Local"],
    ])("counts a declaration written as %s", (_label, source, declared) => {
        expect(declarationsIn(source)).toContain(declared)
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

    // Guards the helper as much as the rule: reading the wrong stream returns an empty report, which
    // would make every "accepts" case above pass without ever seeing a warning.
    it("reports at error severity, so a build fails rather than warns", () => {
        expect(lintTolerant(".x { color: var(--ks-nowhere); }").map(warning => [warning.rule, warning.severity]))
            .toEqual([["ks/no-undeclared-custom-property", "error"]])
    })

    it("accepts a token the file under lint declares itself, even when it is unsaved", () => {
        expect(warningsFor(".x { --ks-just-typed: red; color: var(--ks-just-typed); }")).toEqual([])
    })

    it("registers a self-declaration whatever its case, since a usage is reported in any case", () => {
        expect(warningsFor(".x { --ks-Just-Typed: red; color: var(--ks-Just-Typed); }")).toEqual([])
    })

    it("reads both the scss and the plain css blocks of a single-file component", () => {
        const sfc = "<template><i /></template>\n<style scoped lang=\"scss\">\n.x { color: var(--ks-nowhere); }\n</style>\n"
            + "<style scoped>\n.y { color: var(--ks-elsewhere); }\n</style>\n"
        expect(warningsFor(sfc, "probe.vue")).toEqual([
            expect.stringContaining("\"--ks-nowhere\" is not declared"),
            expect.stringContaining("\"--ks-elsewhere\" is not declared"),
        ])
    })
})

describe("the stylesheet rule, kestra-tokens/no-undeclared-css-token", () => {
    it("reports the same names as the stylelint rule, wherever CSSTree puts them", () => {
        const tester = new RuleTester({plugins: {css}, language: "css/css"})

        expect(() => tester.run("no-undeclared-css-token", eslintPlugin.rules["no-undeclared-css-token"], {
            valid: [
                {code: ".x { color: var(--ks-text-link); }"},
                {code: ".x { --ks-Just-Typed: red; color: var(--ks-Just-Typed); }"},
            ],
            invalid: [
                {code: ".x { color: var(--ks-nowhere); }", errors: [{messageId: "undeclared", column: 17}]},
                {code: ".x { --local: var(--ks-nowhere); }", errors: [{messageId: "undeclared", column: 19}]},
                {code: ".x { color: var(--ks-text-link, var(--ks-nowhere)); }", errors: [{messageId: "undeclared", column: 37}]},
                {code: ".x { color: var(--ks-nowhere, red); }", errors: [{message: /the fallback is what renders/}]},
            ],
        })).not.toThrow()
    })

    it("lints the plain style blocks and style attributes of an SFC at their own position", async () => {
        const sfc = "<template>\n    <i style=\"color: var(--ks-in-attribute)\" />\n</template>\n"
            + "<style lang=\"scss\">\n.x { color: var(--ks-in-scss); }\n</style>\n"
            + "<style scoped>\n.y { color: v-bind(color); background: var(--ks-in-block); }\n</style>\n"
        const [result] = await new ESLint().lintText(sfc, {filePath: "src/probe.vue"})

        expect(result.messages.filter(m => m.ruleId === "kestra-tokens/no-undeclared-css-token")
            .map(({line, column, message}) => [line, column, message.slice(0, message.indexOf("\" "))]))
            .toEqual([[2, 26, "\"--ks-in-attribute"], [8, 44, "\"--ks-in-block"]])
        expect(result.messages.filter(m => m.ruleId?.startsWith("css/"))).toEqual([])
    })

    it("lints each style block and style attribute as its own stylesheet", async () => {
        const sfc = "<template>\n    <i style=\"color: red\" />\n</template>\n"
            + "<style>\n@import \"./a.css\";\n.x { color: red; }\n</style>\n"
            + "<style scoped>\n@import \"./a.css\";\n.y { color: red; }\n</style>\n"
        const [result] = await new ESLint({fix: true}).lintText(sfc, {filePath: "src/probe.vue"})

        expect(result.messages.filter(m => m.ruleId?.startsWith("css/"))).toEqual([])
        expect(result.output ?? sfc).toBe(sfc)
    })
})

describe("the eslint rule for scripts", () => {
    it("covers the tokens stylelint cannot see", () => {
        const tester = new RuleTester({languageOptions: {ecmaVersion: 2022, sourceType: "module"}})

        expect(() => tester.run("no-undeclared-ks-token", eslintPlugin.rules["no-undeclared-ks-token"], {
            valid: [
                {code: "cssVar(\"--ks-text-link\")"},
                {code: "const css = \"color: var(--ks-text-link)\""},
                // A name nothing else declares, used in the same file: the case fails if
                // declarationsIn stops registering rather than passing for want of a usage.
                {code: "el.style.setProperty(\"--ks-probe-hook\", v); const css = \"color: var(--ks-probe-hook)\""},
                {code: "el.style.setProperty(\"--ks-Probe-Hook\", v); const css = \"color: var(--ks-Probe-Hook)\""},
            ],
            invalid: [
                {code: "cssVar(\"--ks-nowhere\")", errors: [{messageId: "undeclared"}]},
                {code: "const css = \"color: var(--ks-nowhere)\"", errors: [{messageId: "undeclared"}]},
                {code: "const css = `border: 1px solid var(--ks-nowhere)`", errors: [{messageId: "undeclared"}]},
                {code: "const css = \"color: var(--ks-Nowhere)\"", errors: [{messageId: "undeclared"}]},
            ],
        })).not.toThrow()
    })
})
