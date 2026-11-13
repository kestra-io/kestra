import {describe, expect, it} from "vitest"
import {dirname, resolve} from "node:path"
import {fileURLToPath} from "node:url"
// @ts-expect-error the script is plain .mjs shared with the pre-commit hook
import {analyze} from "../../scripts/dependency-split/check.mjs"

const UI = resolve(dirname(fileURLToPath(import.meta.url)), "../..")

describe("dependencies split", () => {
    const {toDependencies, toDevDependencies, staleAllowed} = analyze(UI) as {
        toDependencies: string[]
        toDevDependencies: string[]
        staleAllowed: string[]
    }

    it("keeps packages imported by runtime code out of devDependencies", () => {
        expect(toDependencies, `Run \`npm run check:dep-split -- --write\` in ui/, the SBOM is generated with --omit dev:\n${toDependencies.join("\n")}`).toEqual([])
    })

    it("keeps packages that no runtime code imports out of dependencies", () => {
        expect(toDevDependencies, `Run \`npm run check:dep-split -- --write\` in ui/, or add to ALLOWED_RUNTIME with the reason:\n${toDevDependencies.join("\n")}`).toEqual([])
    })

    it("lists only allowed packages that are still in dependencies", () => {
        expect(staleAllowed, "Remove these from ALLOWED_RUNTIME").toEqual([])
    })
})
