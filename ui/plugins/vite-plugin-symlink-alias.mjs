// @ts-check
import fs from "node:fs"
import path from "node:path"

/**
 * Discovers every symlinked package in a node_modules directory (npm workspaces /
 * `npm link`), including scoped (`@scope/pkg`) packages. Returns the symlink path
 * (`sym`, what Vite keys the module graph by under `preserveSymlinks`) alongside its
 * resolved real path (`real`, what the file watcher reports edits under).
 * @param {string} nodeModulesDir
 * @returns {{real: string, sym: string}[]}
 */
function findLinkedPackages(nodeModulesDir) {
    /** @type {{real: string, sym: string}[]} */
    const links = []
    /** @param {string} dir */
    const scan = (dir) => {
        let entries
        try {
            entries = fs.readdirSync(dir, {withFileTypes: true})
        } catch {
            return // node_modules (or a scope dir) may not exist yet
        }
        for (const entry of entries) {
            const full = path.join(dir, entry.name)
            if (entry.isSymbolicLink()) {
                try {
                    links.push({real: fs.realpathSync(full), sym: full})
                } catch {
                    // dangling symlink — ignore
                }
            } else if (entry.isDirectory() && entry.name.startsWith("@")) {
                scan(full) // scoped packages live one level deeper
            }
        }
    }
    scan(nodeModulesDir)
    return links
}

/**
 * Dev-only HMR fix for symlinked workspace packages (e.g. @kestra-io/design-system,
 * @kestra-io/topology). With `resolve.preserveSymlinks`, Vite keys the module graph
 * under the node_modules symlink path while the file watcher reports edits under the
 * real (realpath) location. The mismatch means edits never map to a module and HMR is
 * silently dropped. This remaps the watcher's real path back to the symlink path the
 * module graph uses, so the correct boundary modules are invalidated. The set of linked
 * packages is discovered automatically from node_modules.
 * @param {string} root project root containing the `node_modules` directory to scan
 * @returns {import("vite").Plugin}
 */
export function symlinkAlias(root) {
    const links = findLinkedPackages(path.resolve(root, "node_modules"))
    return {
        name: "kestra:symlink-alias-hmr",

        hotUpdate({file, modules}) {
            for (const {real, sym} of links) {
                if (file.startsWith(real + path.sep)) {
                    const remapped = this.environment.moduleGraph.getModulesByFile(sym + file.slice(real.length))
                    if (remapped?.size) return [...remapped]
                }
            }
            return modules
        },
    }
}
