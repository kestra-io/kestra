import {mkdtempSync, writeFileSync} from "node:fs"
import {tmpdir} from "node:os"
import {dirname, join, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {describe, expect, it} from "vitest"
import {bootstrapUtilityUses, bootstrapVariableReads, definedClasses, type UtilityUse} from "./bootstrapGuard"

const UI = resolve(dirname(fileURLToPath(import.meta.url)), "../../..")
const ROOTS = ["src", "packages/design-system/src", "packages/topology/src"]
const UTILITIES = join(UI, "packages/design-system/src/assets/styles/_utilities.css")

const describeUse = ({at, name}: UtilityUse) => `${at} (${name})`

/**
 * @todo Once the last utility is migrated and _utilities.css is deleted (kestra-io/kestra#20834), replace the two
 * _utilities.css tests with one that allows no Bootstrap utility at all, as ui-ee's bootstrapGuard.spec.ts does.
 */
describe("design system", () => {
    it("reads no Bootstrap --kbs-* variable", () => {
        const offenders = bootstrapVariableReads(UI, ROOTS)

        expect(offenders, `Use the --ks-* token holding the same value (see ui/AGENTS.md):\n${offenders.join("\n")}`).toEqual([])
    })

    it("uses only the Bootstrap utilities that _utilities.css still defines", () => {
        const defined = definedClasses(UTILITIES)
        const offenders = bootstrapUtilityUses(UI, ROOTS).filter(({name}) => !defined.has(name)).map(describeUse)

        expect(offenders, `Bootstrap is gone: write these as scoped CSS with --ks-* tokens (see ui/AGENTS.md):\n${offenders.join("\n")}`).toEqual([])
    })

    it("keeps no rule in _utilities.css once its last use is migrated", () => {
        const used = new Set(bootstrapUtilityUses(UI, ROOTS).map(({name}) => name))
        const unused = [...definedClasses(UTILITIES)].filter((name) => !used.has(name))
        const remaining = unused.filter((name) => name !== "m-2")

        expect(remaining, `Delete these rules from _utilities.css, nothing uses them any more:\n${remaining.join("\n")}`).toEqual([])
    })

    it("finds utilities in class attributes, :class keys and script strings, and leaves other classes alone", () => {
        const dir = mkdtempSync(join(tmpdir(), "bootstrap-guard-"))
        writeFileSync(join(dir, "Component.vue"), [
            "<template>",
            "    <div class=\"d-flex card-header\" :class=\"{'d-none': hidden}\" />",
            "</template>",
            "<script setup lang=\"ts\">",
            "    const margin = \"mb-3\"",
            "    const tooltip = {axisPointer: {type: \"shadow\"}}",
            "</script>",
        ].join("\n"))

        expect(bootstrapUtilityUses(dir).map(describeUse)).toEqual([
            "Component.vue:2 (d-flex)",
            "Component.vue:2 (d-none)",
            "Component.vue:5 (mb-3)",
        ])
    })
})
