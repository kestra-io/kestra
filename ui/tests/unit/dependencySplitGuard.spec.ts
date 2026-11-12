import {describe, expect, it} from "vitest"
import {existsSync, readdirSync, readFileSync} from "node:fs"
import {dirname, join, resolve} from "node:path"
import {fileURLToPath} from "node:url"

const UI = resolve(dirname(fileURLToPath(import.meta.url)), "../..")
const RUNTIME_ROOTS = ["src", "packages/design-system/src", "packages/topology/src"]
const NOT_RUNTIME = /(\.spec\.|\.test\.|\.stories\.|[\\/]tests?[\\/]|\.storybook)/
const SOURCE = /\.(vue|ts|tsx|js|mjs|jsx|css|scss)$/
const IMPORT = /(?:from\s+|import\s*\(?\s*|require\(\s*|@use\s+|@import\s+)["']([^"'./][^"']*)["']/g

/** Packages that are not imported by runtime code but must stay in `dependencies`, each with the reason. */
const ALLOWED_RUNTIME: Record<string, string> = {}

function packageName(specifier: string) {
    const parts = specifier.split("/")
    return specifier.startsWith("@") ? parts.slice(0, 2).join("/") : parts[0]
}

function importedByRuntime() {
    const names = new Set<string>()
    const walk = (dir: string) => {
        if (!existsSync(dir)) return
        for (const entry of readdirSync(dir, {withFileTypes: true})) {
            const path = join(dir, entry.name)
            if (entry.isDirectory()) {
                if (entry.name !== "node_modules") walk(path)
            } else if (SOURCE.test(entry.name) && !NOT_RUNTIME.test(path)) {
                for (const match of readFileSync(path, "utf8").matchAll(IMPORT)) names.add(packageName(match[1]))
            }
        }
    }
    RUNTIME_ROOTS.forEach((root) => walk(join(UI, root)))
    return names
}

describe("dependencies split", () => {
    const pkg = JSON.parse(readFileSync(join(UI, "package.json"), "utf8"))
    const imported = importedByRuntime()
    const dependencies = Object.keys(pkg.dependencies ?? {})
    const devDependencies = Object.keys(pkg.devDependencies ?? {})

    it("keeps packages imported by runtime code out of devDependencies", () => {
        const offenders = devDependencies.filter((name) => imported.has(name))
        expect(offenders, `Move to "dependencies" in ui/package.json, the SBOM is generated with --omit dev:\n${offenders.join("\n")}`).toEqual([])
    })

    it("keeps packages that no runtime code imports out of dependencies", () => {
        const offenders = dependencies.filter((name) => !imported.has(name) && !(name in ALLOWED_RUNTIME))
        expect(offenders, `Move to "devDependencies" in ui/package.json, or add to ALLOWED_RUNTIME with the reason:\n${offenders.join("\n")}`).toEqual([])
    })

    it("lists only allowed packages that are still in dependencies", () => {
        const stale = Object.keys(ALLOWED_RUNTIME).filter((name) => !dependencies.includes(name))
        expect(stale, "Remove these from ALLOWED_RUNTIME").toEqual([])
    })
})
