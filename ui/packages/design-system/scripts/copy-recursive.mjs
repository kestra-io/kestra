import {existsSync, statSync, readdirSync, readFileSync, mkdirSync, writeFileSync} from "fs"
import {join} from "path"

/**
 * Recursively copies files and directories from a source to a destination.
 * @param {string} src - The source path.
 * @param {string} dest - The destination path.
 * @param {Object} options - Optional settings.
 * @param {Function} options.filter - A function to filter which files/directories to copy.
 */
export function copyRecursive(src, dest, options = {}) {
    if (!existsSync(src)) return
    const stat = statSync(src)
    if (stat.isDirectory()) {
        if (options.filter && !options.filter(src)) return
        mkdirSync(dest, {recursive: true})
        for (const entry of readdirSync(src)) {
            copyRecursive(join(src, entry), join(dest, entry), options)
        }
    } else {
        if (options.filter && !options.filter(src)) return
        writeFileSync(dest, readFileSync(src))
    }
}