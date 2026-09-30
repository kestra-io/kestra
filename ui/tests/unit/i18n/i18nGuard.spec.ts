import {existsSync, mkdtempSync, readdirSync, rmSync, writeFileSync} from "node:fs"
import {tmpdir} from "node:os"
import {dirname, join, relative, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {describe, expect, it} from "vitest"
import {findI18nViolations} from "./i18nGuard"

const UI = resolve(dirname(fileURLToPath(import.meta.url)), "../../..")
const PACKAGES = join(UI, "packages")
const ROOTS = [
    join(UI, "src"),
    ...readdirSync(PACKAGES).map((name) => join(PACKAGES, name, "src")).filter(existsSync),
]

const TWO_SCRIPT_BLOCKS = `<template>
    <div>
        <i18n-t keypath="first" />
        <I18nT keypath="second" />
    </div>
</template>

<script lang="ts">
    export default {name: "Two"}
</script>

<script setup lang="ts">
    const label = $t("third")
</script>
`

describe("i18n", () => {
    it("follows the i18n conventions across ui/src and ui/packages", () => {
        const offenders = ROOTS.flatMap((root) => findI18nViolations(root).map((offender) => `${relative(UI, root)}/${offender}`))

        expect(offenders, `Fix the i18n usage, see ui/AGENTS.md:\n${offenders.join("\n")}`).toEqual([])
    })

    it("reports every offending line, including one in a second script block", () => {
        const dir = mkdtempSync(join(tmpdir(), "i18n-guard-"))
        writeFileSync(join(dir, "Two.vue"), TWO_SCRIPT_BLOCKS)

        const offenders = findI18nViolations(dir)
        rmSync(dir, {recursive: true, force: true})

        expect(offenders.map((offender) => offender.split(" ")[0])).toEqual(["Two.vue:3", "Two.vue:4", "Two.vue:13"])
    })
})
