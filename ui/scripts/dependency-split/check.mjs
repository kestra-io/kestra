import {execFileSync} from "node:child_process"
import {existsSync, readdirSync, readFileSync, writeFileSync} from "node:fs"
import {dirname, join, resolve} from "node:path"
import {fileURLToPath} from "node:url"

const RUNTIME_ROOTS = ["src", "packages/design-system/src", "packages/topology/src"]
const NOT_RUNTIME = /(\.spec\.|\.test\.|\.stories\.|[\\/]tests?[\\/]|\.storybook)/
const SOURCE = /\.(vue|ts|tsx|js|mjs|jsx|css|scss)$/
const IMPORT = /(?:from\s+|import\s*\(?\s*|require\(\s*|@use\s+|@import\s+)["']([^"'./][^"']*)["']/g

/** Packages that are not imported by runtime code but must stay in `dependencies`, each with the reason. */
export const ALLOWED_RUNTIME = {}

function packageName(specifier) {
    const parts = specifier.split("/")
    return specifier.startsWith("@") ? parts.slice(0, 2).join("/") : parts[0]
}

export function importedByRuntime(uiRoot) {
    const names = new Set()
    const walk = (dir) => {
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
    RUNTIME_ROOTS.forEach((root) => walk(join(uiRoot, root)))
    return names
}

export function analyze(uiRoot) {
    const pkg = JSON.parse(readFileSync(join(uiRoot, "package.json"), "utf8"))
    const imported = importedByRuntime(uiRoot)
    const dependencies = Object.keys(pkg.dependencies ?? {})
    const devDependencies = Object.keys(pkg.devDependencies ?? {})
    return {
        pkg,
        toDependencies: devDependencies.filter((name) => imported.has(name)),
        toDevDependencies: dependencies.filter((name) => !imported.has(name) && !(name in ALLOWED_RUNTIME)),
        staleAllowed: Object.keys(ALLOWED_RUNTIME).filter((name) => !dependencies.includes(name)),
    }
}

function move(pkg, names, from, to) {
    for (const name of names) {
        const entries = Object.entries(pkg[to])
        const index = entries.findIndex(([key]) => key.localeCompare(name) > 0)
        entries.splice(index === -1 ? entries.length : index, 0, [name, pkg[from][name]])
        pkg[to] = Object.fromEntries(entries)
        delete pkg[from][name]
    }
}

function main() {
    const uiRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..")
    const write = process.argv.includes("--write")
    const {pkg, toDependencies, toDevDependencies, staleAllowed} = analyze(uiRoot)

    if (staleAllowed.length) {
        console.error(`Remove from ALLOWED_RUNTIME in scripts/dependency-split/check.mjs:\n${staleAllowed.join("\n")}`)
        process.exit(1)
    }
    if (!toDependencies.length && !toDevDependencies.length) return

    if (!write) {
        if (toDependencies.length) console.error(`Imported by runtime code, move to "dependencies":\n${toDependencies.join("\n")}`)
        if (toDevDependencies.length) console.error(`Not imported by runtime code, move to "devDependencies":\n${toDevDependencies.join("\n")}`)
        console.error("Run `npm run check:dep-split -- --write` to move them and refresh package-lock.json.")
        process.exit(1)
    }

    move(pkg, toDependencies, "devDependencies", "dependencies")
    move(pkg, toDevDependencies, "dependencies", "devDependencies")
    writeFileSync(join(uiRoot, "package.json"), `${JSON.stringify(pkg, null, 4)}\n`)
    console.log(`Moved to dependencies: ${toDependencies.join(", ") || "none"}; to devDependencies: ${toDevDependencies.join(", ") || "none"}.`)

    if (!process.argv.includes("--no-lock")) {
        execFileSync("npm", ["install", "--package-lock-only", "--ignore-scripts"], {cwd: uiRoot, stdio: "inherit"})
    }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main()
