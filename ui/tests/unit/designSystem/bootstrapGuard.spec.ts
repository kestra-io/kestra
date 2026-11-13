import {mkdtempSync, writeFileSync} from "node:fs"
import {tmpdir} from "node:os"
import {dirname, join, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {describe, expect, it} from "vitest"
import {bootstrapUtilityUses, bootstrapVariableReads, type UtilityUse} from "./bootstrapGuard"

const UI = resolve(dirname(fileURLToPath(import.meta.url)), "../../..")
const ROOTS = ["src", "packages/design-system/src", "packages/topology/src"]

const describeUse = ({at, name}: UtilityUse) => `${at} (${name})`

describe("design system", () => {
    it("reads no Bootstrap --kbs-* variable", () => {
        const offenders = bootstrapVariableReads(UI, ROOTS)

        expect(offenders, `Use the --ks-* token holding the same value (see ui/AGENTS.md):\n${offenders.join("\n")}`).toEqual([])
    })

    it("uses no Bootstrap utility classes", () => {
        const offenders = bootstrapUtilityUses(UI, ROOTS).map(describeUse)

        expect(offenders, `Bootstrap is gone: write these as scoped CSS with --ks-* tokens (see ui/AGENTS.md):\n${offenders.join("\n")}`).toEqual([])
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
