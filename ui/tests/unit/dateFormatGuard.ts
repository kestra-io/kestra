import {readFileSync, readdirSync} from "node:fs"
import {join, relative, sep} from "node:path"

/** Date formatting that bypasses `dateUtils.dateFilter`, and with it the timezone and date format chosen in Settings. */
const PATTERNS = [
    /\bdayjs\((?:[^()]|\([^()]*\))*\)\s*(?:\.tz\((?:[^()]|\([^()]*\))*\)\s*)?\.format\(/,
    /\bnew Date\((?:[^()]|\([^()]*\))*\)\s*\.toLocale(?:Date|Time)?String\(/,
    /\.toLocale(?:Date|Time)String\(/,
    /\bIntl\.DateTimeFormat\b/,
]

const sources = (dir: string): string[] =>
    readdirSync(dir, {withFileTypes: true}).flatMap((entry) => {
        const full = join(dir, entry.name)
        if (entry.isDirectory()) return sources(full)
        return /\.(vue|ts|js)$/.test(entry.name) && !/\.(spec|test|stories)\.[jt]s$/.test(entry.name) ? [full] : []
    })

/** Returns `path:line` for every date formatted without `dateUtils`, paths relative to `srcDir` with `/` separators on every OS. */
export const findRawDateFormatting = (srcDir: string): string[] =>
    sources(srcDir).flatMap((file) =>
        readFileSync(file, "utf8")
            .split("\n")
            .flatMap((line, i) => (PATTERNS.some((re) => re.test(line)) ? [`${relative(srcDir, file).split(sep).join("/")}:${i + 1}`] : [])),
    )
