import {existsSync, readdirSync} from "node:fs"
import {dirname, join, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {describe, expect, it} from "vitest"

const UI = resolve(dirname(fileURLToPath(import.meta.url)), "../..")
const TEST_ROOTS = [
    {tests: "tests/unit", src: "src"},
    {tests: "tests/storybook", src: "src"},
    {tests: "packages/design-system/tests/units", src: "packages/design-system/src"},
    {tests: "packages/design-system/tests/storybook", src: "packages/design-system/src/components"},
    {tests: "packages/topology/tests/units", src: "packages/topology/src"},
]
const TEST_FILE = /\.(spec|test|stories)\.tsx?$/
const SOURCE_EXTENSIONS = [".vue", ".ts", ".tsx", ".js"]

describe("spec location", () => {
    it("keeps specs and stories next to the src file they cover", () => {
        const misplaced = TEST_ROOTS.flatMap(({tests, src}) =>
            readdirSync(join(UI, tests), {recursive: true, encoding: "utf8"})
                .filter((file) => TEST_FILE.test(file))
                .filter((file) => {
                    const subject = join(UI, src, file.replace(TEST_FILE, ""))
                    return SOURCE_EXTENSIONS.some((ext) => existsSync(subject + ext))
                })
                .map((file) => `${tests}/${file}`),
        )

        expect(misplaced, `Move these next to their source file, see ui/AGENTS.md:\n${misplaced.join("\n")}`).toEqual([])
    })
})
