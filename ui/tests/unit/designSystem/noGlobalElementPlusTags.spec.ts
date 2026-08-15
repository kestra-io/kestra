import {describe, it, expect} from "vitest"
import {readdirSync, readFileSync} from "node:fs"
import {join, relative} from "node:path"

// The design system installs only Element Plus's global config, not its ~124 components, so
// nothing is globally registered under `el-*`. That is safe exactly as long as shipped code
// keeps addressing Element Plus through the Ks* wrappers, which import each El* component by
// name. A raw `<el-select>` in src/ would render as an unresolved element instead — silently,
// since Vue only warns. Storybook fixtures are exempt: some render raw tags to exercise
// theming, and .storybook/preview.jsx registers the full library for them.
describe("Element Plus global registration", () => {
    const root = join(__dirname, "../../..")

    const vueFilesIn = (dir: string) => {
        let entries
        try {
            entries = readdirSync(join(root, dir), {recursive: true, encoding: "utf8"})
        } catch {
            return []
        }
        return entries
            .filter((entry) => entry.endsWith(".vue") && !entry.includes("node_modules"))
            .map((entry) => join(root, dir, entry))
    }

    it("should not use <el-*> tags in shipped code", () => {
        const files = [
            ...vueFilesIn("src"),
            ...vueFilesIn("packages/design-system/src"),
            ...vueFilesIn("packages/topology/src"),
        ]
        expect(files.length).toBeGreaterThan(100)

        const offenders = files
            .filter((file) => /<el-[a-z-]+/.test(readFileSync(file, "utf8")))
            .map((file) => relative(root, file))

        expect(
            offenders,
            "Use the Ks* wrapper, or import the El* component by name; <el-*> relies on a global registration the design system no longer performs.",
        ).toEqual([])
    })
})
