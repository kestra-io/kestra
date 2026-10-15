import {readFileSync, readdirSync} from "node:fs"
import {join, relative} from "node:path"

/**
 * Class names shaped like a Bootstrap 5 utility, defined or not. Bare `shadow` is left out because it is also an
 * ECharts option value, and `text-truncate` because this build never compiled it.
 */
const BOOTSTRAP_UTILITY = new RegExp(`^(?:${[
    "[mp][tbsexy]?-(?:(?:sm|md|lg|xl|xxl)-)?(?:[0-5]|auto)",
    "d-(?:(?:sm|md|lg|xl|xxl)-)?(?:none|inline|inline-block|block|grid|inline-grid|table|table-row|table-cell|flex|inline-flex)",
    "gap-[0-5]",
    "flex-(?:row|column|row-reverse|column-reverse|wrap|nowrap|wrap-reverse|fill|grow-[01]|shrink-[01])",
    "(?:justify-content|align-items|align-self|align-content)-(?:start|end|center|between|around|evenly|baseline|stretch)",
    "(?:w|h)-(?:25|50|75|100|auto)",
    "(?:mw|mh|vw|vh|min-vw|min-vh)-100",
    "fw-(?:bold|bolder|semibold|medium|normal|light|lighter)",
    "fs-[1-6]",
    "lh-(?:1|sm|base|lg)",
    "text-(?:start|end|center|nowrap|wrap|break|lowercase|uppercase|capitalize|decoration-(?:none|underline|line-through)|primary|secondary|success|danger|warning|info|light|dark|body|muted|white|black|reset)",
    "position-(?:static|relative|absolute|fixed|sticky)",
    "(?:top|bottom|start|end)-(?:0|50|100)",
    "z-(?:n1|[0-3])",
    "overflow-(?:auto|hidden|visible|scroll)",
    "border(?:-(?:top|end|bottom|start))?-0",
    "bg-(?:transparent|primary|secondary|success|danger|warning|info|light|dark|body|white|black)",
    "shadow-(?:sm|lg|none)",
    "align-(?:baseline|top|middle|bottom|text-top|text-bottom)",
    "pe-(?:none|auto)",
    "font-monospace",
].join("|")})$`)

export interface UtilityUse {
    at: string
    name: string
}

const sources = (dir: string): string[] =>
    readdirSync(dir, {withFileTypes: true}).flatMap((entry) => {
        const full = join(dir, entry.name)
        if (entry.isDirectory()) return sources(full)
        return /\.(vue|s?css|ts|js)$/.test(entry.name) && !/\.(spec|test|stories)\.[jt]s$/.test(entry.name) ? [full] : []
    })

/** Every source line under the roots, located as `path:line` relative to `base`. */
const linesOf = (base: string, roots: string[]) =>
    roots.flatMap((root) => sources(join(base, root))).flatMap((file) =>
        readFileSync(file, "utf8").split("\n").map((text, index) => ({at: `${relative(base, file)}:${index + 1}`, text})),
    )

/**
 * The Bootstrap utilities written in a quoted string under the roots: a class attribute, a `:class` object key or a
 * class string in script. Read line by line, so a class attribute split over several lines is only read in part.
 */
export const bootstrapUtilityUses = (base: string, roots: string[] = ["."]): UtilityUse[] =>
    linesOf(base, roots).flatMap(({at, text}) =>
        [...text.matchAll(/(["'`])((?:(?!\1).)*)\1/g)]
            .flatMap(([, , value]) => value.split(/[^\w-]+/))
            .filter((name) => BOOTSTRAP_UTILITY.test(name))
            .map((name) => ({at, name})),
    )

/** `path:line` of every `--kbs-*` variable under the roots. */
export const bootstrapVariableReads = (base: string, roots: string[] = ["."]): string[] =>
    linesOf(base, roots).filter(({text}) => /--kbs-[\w-]+/.test(text)).map(({at}) => at)

/** The class names a stylesheet defines as rules, including those inside media queries. */
export const definedClasses = (stylesheet: string): Set<string> =>
    new Set([...readFileSync(stylesheet, "utf8").matchAll(/^\s*\.([a-z0-9-]+) \{/gm)].map(([, name]) => name))
