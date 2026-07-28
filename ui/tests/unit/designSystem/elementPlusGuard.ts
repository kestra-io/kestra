import {readFileSync, readdirSync} from "node:fs"
import {join, relative} from "node:path"

/** Shapes of direct Element Plus usage banned in feature code. `kel-*` and `--el-*` never match. */
const PATTERNS = [
    /(?:from|import)\s*["']@?element-plus(?:\/[^"']*)?["']/, // imports, incl. @element-plus/icons-vue
    /<\/?el-[a-z][\w-]*/, // <el-button> tags
    /["']el-[a-z][\w-]*/, // string refs: component="el-button", escaped class strings
    /class\s*=\s*\\?["'][^"'\\]*\bel-[a-z][\w-]*/, // el-* in class attributes
    /\.el-[a-z][\w-]*/, // .el-* selectors, incl. :deep(.el-foo)
]

const sources = (dir: string): string[] =>
    readdirSync(dir, {withFileTypes: true}).flatMap((entry) => {
        const full = join(dir, entry.name)
        if (entry.isDirectory()) return sources(full)
        return /\.(vue|ts|js)$/.test(entry.name) ? [full] : []
    })

/** Returns `path:line (tokens)` for every file using Element Plus directly; empty when clean. */
export const findElementPlusUsage = (srcDir: string): string[] =>
    sources(srcDir).flatMap((file) => {
        const lines = readFileSync(file, "utf8").split("\n")
        const hits = lines.flatMap((line, i) => (PATTERNS.some((re) => re.test(line)) ? [i] : []))
        if (!hits.length) return []

        const tokens = [...new Set(hits.flatMap((i) => lines[i].match(/@?element-plus|el-[a-z][\w-]*/g) ?? []))]
        return [`${relative(srcDir, file)}:${hits[0] + 1}${tokens.length ? ` (${tokens.join(", ")})` : ""}`]
    })
