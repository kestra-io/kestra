import {describe, expect, it} from "vitest"
import {classify} from "./baseline.mjs"

const ROOT = "/repo/ui"
const unknown = (path, row, prop) => `${path}(${row},5): error TS2353: Object literal may only specify known properties, and '${prop}' does not exist in type '{}'.`

describe("classify", () => {
    it("counts unknown props per file and leaves every other error to fail the check", () => {
        const output = [
            unknown("src/A.vue", 3, "trigger"),
            unknown("src/A.vue", 9, "ariaLabel"),
            "src/B.vue(1,1): error TS2561: Object literal may only specify known properties, but 'tittle' does not exist. Did you mean to write 'title'?",
            "src/C.ts(4,2): error TS2322: Type 'string' is not assignable to type 'number'.",
            "error TS5083: Cannot read file 'tsconfig.json'.",
        ].join("\n")
        const {counts, others} = classify(output, ROOT)
        expect(counts).toEqual({"src/A.vue": 2, "src/B.vue": 1})
        expect(others).toEqual([
            "src/C.ts(4,2): error TS2322: Type 'string' is not assignable to type 'number'.",
            "error TS5083: Cannot read file 'tsconfig.json'.",
        ])
    })

    it("counts a file reached through a workspace symlink once", () => {
        const real = (path) => path.replace("/repo/ui/node_modules/@kestra-io/design-system", "/repo/ui/packages/design-system")
        const output = [
            unknown("packages/design-system/src/X.vue", 2, "size"),
            unknown("node_modules/@kestra-io/design-system/src/X.vue", 2, "size"),
        ].join("\n")
        expect(classify(output, ROOT, real).counts).toEqual({"packages/design-system/src/X.vue": 1})
    })
})
