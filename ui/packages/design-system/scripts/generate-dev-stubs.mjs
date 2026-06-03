/**
 * Generates stub files in dist/ that re-export from src/.
 * This keeps package.json exports stable while allowing live source editing during development.
 * Run automatically via the `prepare` script (local installs only, not when installed as a dependency).
 */

// Skip during `npm publish` / `npm pack` — the real build (prepublishOnly) should own dist then.
if (["publish", "pack"].includes(process.env.npm_command)) {
    console.log("[dev-stubs] Skipping stub generation during publish/pack.")
    process.exit(0)
}

import {readFileSync, mkdirSync, writeFileSync, existsSync} from "fs"
import {dirname, join, relative} from "path"
import {fileURLToPath} from "url"

const __dirname = dirname(fileURLToPath(import.meta.url))
const packageRoot = join(__dirname, "..")
const pkg = JSON.parse(
    readFileSync(join(packageRoot, "package.json"), "utf-8"),
)

const SRC_EXTENSIONS = [".vue", ".ts", ".js"]

let created = 0
let skipped = 0

for (const [exportKey, exportValue] of Object.entries(pkg.exports)) {
    if (exportKey === "./package.json") continue
    if (typeof exportValue !== "string") continue

    // exportValue is like ./dist/components/Basic/KsButton/KsButton.js
    const distRelPath = exportValue.replace(/^\.\//, "")
    if (!distRelPath.startsWith("dist/") || !distRelPath.endsWith(".js")) {
        skipped++
        continue
    }

    const distAbsPath = join(packageRoot, distRelPath)

    // Map dist/x/y.js → src/x/y, then try extensions
    const srcRelBase = distRelPath.replace(/^dist\//, "src/").replace(/\.js$/, "")

    let srcRelPath = null
    for (const ext of SRC_EXTENSIONS) {
        const candidate = srcRelBase + ext
        if (existsSync(join(packageRoot, candidate))) {
            srcRelPath = candidate
            break
        }
    }

    if (!srcRelPath) {
        console.warn(`[dev-stubs] No source found for "${exportKey}" → ${exportValue}`)
        skipped++
        continue
    }

    // Relative path from the stub file location to the source file
    const distDir = dirname(distAbsPath)
    const srcAbsPath = join(packageRoot, srcRelPath)
    const relPath = relative(distDir, srcAbsPath).replace(/\\/g, "/")

    const isVue = srcRelPath.endsWith(".vue")
    const exports = isVue
        ? `export { default } from '${relPath}';\nexport * from '${relPath}';\n`
        : `export * from '${relPath}';\n`

    mkdirSync(distDir, {recursive: true})
    writeFileSync(distAbsPath, exports, "utf-8")

    // Declaration stub + declaration map so "Go to Source" jumps to the .vue/.ts file
    const dtsPath = distAbsPath.replace(/\.js$/, ".d.ts")
    const dtsMapPath = dtsPath + ".map"
    const dtsFileName = dtsPath.split("/").pop()

    const declarationMap = JSON.stringify({
        version: 3,
        file: dtsFileName,
        sourceRoot: "",
        sources: [relPath],
        mappings: "",
    })

    writeFileSync(dtsPath, exports + `//# sourceMappingURL=${dtsFileName}.map\n`, "utf-8")
    writeFileSync(dtsMapPath, declarationMap, "utf-8")

    created++
}

console.log(`[dev-stubs] Created ${created} stub(s), skipped ${skipped}.`)
