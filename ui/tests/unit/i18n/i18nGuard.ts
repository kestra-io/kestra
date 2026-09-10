import {readFileSync, readdirSync} from "node:fs"
import {join, relative} from "node:path"

type Block = "template" | "script" | "source"

const RULES: {block: Block; pattern: RegExp; fix: string}[] = [
    {
        block: "template",
        pattern: /<(?:i18n-t|I18nT)\b/g,
        fix: "render the string with $t() and named arguments, <i18n-t> is not used",
    },
    {
        block: "template",
        pattern: /(?<![\w$.])t\(/g,
        fix: "templates call $t(), the t() from useI18n() belongs in <script>",
    },
    {
        block: "template",
        pattern: /\sv-t\s*=/g,
        fix: "the v-t directive is not used, call $t() in the expression instead",
    },
    {
        block: "script",
        pattern: /(?<![\w.])\$t\(/g,
        fix: "<script> calls t() from useI18n(), $t belongs in templates",
    },
    {
        block: "source",
        pattern: /<i18n[\s>]/g,
        fix: "keys live in the translation files, not in a component's <i18n> block",
    },
]

/** Comments blanked out so a rule cannot fire on prose, newlines kept so line numbers stay right. */
const withoutComments = (source: string): string =>
    source
        .replace(/<!--[\s\S]*?-->|\/\*[\s\S]*?\*\//g, (comment) => comment.replace(/[^\n]/g, " "))
        .replace(/(^|[^:])\/\/[^\n]*/g, (comment, before: string) => before.padEnd(comment.length))

/** Each block's body with its offset in the source, so a file with several `<script>` blocks still reports real lines. */
const segmentsOf = (source: string): Record<Block, {body: string; start: number}[]> => {
    const bodies = (pattern: RegExp) =>
        [...source.matchAll(pattern)].map((match) => ({body: match[2], start: (match.index ?? 0) + match[1].length}))

    return {
        source: [{body: source, start: 0}],
        template: bodies(/(<template>)([\s\S]*)(?:<\/template>)/g),
        script: bodies(/(<script[^>]*>)([\s\S]*?)(?:<\/script>)/g),
    }
}

const vueFilesIn = (dir: string): string[] =>
    readdirSync(dir, {withFileTypes: true}).flatMap((entry) => {
        const path = join(dir, entry.name)
        if (entry.isDirectory()) return vueFilesIn(path)
        return entry.name.endsWith(".vue") ? [path] : []
    })

/** `path:line (fix)` for every rule a component under `srcDir` breaks; empty when they all follow them. */
export const findI18nViolations = (srcDir: string): string[] =>
    vueFilesIn(srcDir).flatMap((path) => {
        const source = withoutComments(readFileSync(path, "utf8"))
        const segments = segmentsOf(source)

        return RULES.flatMap(({block, pattern, fix}) =>
            segments[block].flatMap(({body, start}) =>
                [...body.matchAll(pattern)].map((match) => {
                    const line = source.slice(0, start + (match.index ?? 0)).split("\n").length
                    return `${relative(srcDir, path)}:${line} (${fix})`
                })))
    })
