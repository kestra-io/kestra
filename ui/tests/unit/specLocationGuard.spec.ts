import {existsSync, readdirSync} from "node:fs"
import {dirname, join, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {describe, expect, it} from "vitest"

const UI = resolve(dirname(fileURLToPath(import.meta.url)), "../..")
const TEST_ROOTS = ["tests/unit", "tests/storybook"]
const SOURCE_EXTENSIONS = [".vue", ".ts", ".tsx", ".js"]

describe("spec location", () => {
    it("keeps specs and stories next to the src file they cover", () => {
        const misplaced = TEST_ROOTS.flatMap((root) =>
            readdirSync(join(UI, root), {recursive: true, encoding: "utf8"})
                .filter((file) => /\.(spec|stories)\.tsx?$/.test(file))
                .filter((file) => {
                    const subject = join(UI, "src", file.replace(/\.(spec|stories)\.tsx?$/, ""))
                    return SOURCE_EXTENSIONS.some((ext) => existsSync(subject + ext))
                })
                .map((file) => `${root}/${file}`),
        )

        expect(misplaced, `Move these next to their source file in ui/src, see ui/AGENTS.md:\n${misplaced.join("\n")}`).toEqual([])
    })
})
