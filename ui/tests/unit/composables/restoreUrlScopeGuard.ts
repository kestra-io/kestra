import {readFileSync, readdirSync} from "node:fs"
import {join, relative} from "node:path"

const sources = (dir: string): string[] =>
    readdirSync(dir, {withFileTypes: true}).flatMap((entry) => {
        const full = join(dir, entry.name)
        if (entry.isDirectory()) return sources(full)
        return /\.(vue|ts)$/.test(entry.name) ? [full] : []
    })

const COMPOSABLE = "composables/useRestoreUrl.ts"

/** Returns the `ui/src`-relative path of every file that calls `useRestoreUrl(`. */
export const findRestoreUrlConsumers = (srcDir: string): string[] =>
    sources(srcDir)
        .filter((file) => /\buseRestoreUrl\s*\(/.test(readFileSync(file, "utf8")))
        .map((file) => relative(srcDir, file).split("\\").join("/"))
        .filter((file) => file !== COMPOSABLE)
        .sort()
