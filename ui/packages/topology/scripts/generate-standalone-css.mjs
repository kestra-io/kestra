/**
 * Builds src/standalone.css: the design-system styles a page without the design system needs for the graph.
 * Keeps only what the graph reads; run `npm run generate:standalone` after the design system changes.
 */
import fs from "node:fs"
import path from "node:path"
import {fileURLToPath} from "node:url"
import * as sass from "sass-embedded"

const here = path.dirname(fileURLToPath(import.meta.url))
const ui = path.resolve(here, "../../..")
const nodeModules = path.join(ui, "node_modules")
const topologySrc = path.resolve(here, "../src")
const designSystemSrc = path.join(ui, "packages/design-system/src")
const designSystemStyles = path.join(designSystemSrc, "assets/styles")
const OUTPUT = path.join(topologySrc, "standalone.css")

/** Element Plus teleports tooltips and menus to <body>, so they carry the tokens too. */
const SCOPE = ":where(.ks-topology, .kel-popper)"

/** Token files and the selector each declares on; `html.dark-2` is a Kestra UI theme, so it is left out. */
const TOKEN_FILES = {
    "ks-tokens.css": {from: ":root", to: SCOPE},
    "ks-palette.css": {from: ":root", to: SCOPE},
    "ks-theme-light.css": {from: ":root", to: SCOPE},
    "ks-theme-dark.css": {from: "html.dark", to: `:where(html.dark) ${SCOPE}`},
}

/** Tokens the host sets at runtime on <html> (the Kestra UI font size), with the value to assume without it. */
const HOST_TOKENS = {"--ks-font-scale": "1"}

/** Element Plus styles the graph's tooltips and menus use besides their own: ElTooltip fades, ElDropdown zooms. */
const ELEMENT_INFRASTRUCTURE = ["popper", "icon", "fade-in", "zoom-in"]

/** Unprefixed copies Element Plus emits of a few transitions; its components only use the `kel-` ones. */
const UNPREFIXED_DUPLICATES = /^\.(fade-in-linear-|collapse-transition|horizontal-collapse-transition|v-modal)/

const PAGE_SELECTOR = /^(:root|html\.dark)$/

const read = (file) => fs.readFileSync(file, "utf8")

const SASS_OPTIONS = {loadPaths: [designSystemStyles, nodeModules], style: "expanded", logger: sass.Logger.silent}
const compileScssFile = (file) => sass.compile(file, SASS_OPTIONS).css
const compileScss = (source) => sass.compileString(source, SASS_OPTIONS).css

/** Splits CSS into its top-level `prelude { body }` blocks; a body keeps its nested blocks as text. */
function parseBlocks(css) {
    const source = css.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^@charset[^;]*;/, "")
    const blocks = []
    let depth = 0
    let start = 0
    let open = 0
    for (let i = 0; i < source.length; i++) {
        if (source[i] === "{" && depth++ === 0) open = i
        if (source[i] === "}" && --depth === 0) {
            blocks.push({prelude: source.slice(start, open).trim(), body: source.slice(open + 1, i)})
            start = i + 1
        }
    }
    return blocks
}

const printBlocks = (blocks) => blocks.map(({prelude, body}) => `${prelude} {${body}}`).join("\n")

const namesIn = (text) => new Set(text.match(/--(?:ks|kel)-[\w-]*\w/g))

/** Prefixes of token names the source builds at runtime, such as `--ks-status-${state}`. */
const dynamicPrefixesIn = (text) => [...text.matchAll(/(--(?:ks|kel)-[\w-]*)\$\{/g)].map(([, prefix]) => prefix)

/** The graph's own source files, whose styles and scripts name the tokens it reads. */
function topologyFiles(dir = topologySrc) {
    return fs.readdirSync(dir, {withFileTypes: true}).flatMap((entry) => {
        const file = path.join(dir, entry.name)
        if (entry.isDirectory()) return topologyFiles(file)
        const isSource = /\.(vue|ts|css)$/.test(entry.name) && !/\.(test|stories)\.ts$/.test(entry.name)
        return isSource && file !== OUTPUT ? [file] : []
    })
}

/** The design-system component files the graph renders, including the ones they render themselves. */
function designSystemComponents(files) {
    const index = read(path.join(designSystemSrc, "index.ts"))
    const fileOf = Object.fromEntries([...index.matchAll(/^import (Ks\w+) from "(\.\/components\/[^"]+\.vue)"/gm)]
        .map(([, name, file]) => [name, path.join(designSystemSrc, file)]))
    const pending = files
        .flatMap((file) => [...read(file).matchAll(/import\s*\{([^}]+)\}\s*from\s*"@kestra-io\/design-system"/g)])
        .flatMap(([, names]) => names.split(",").map((name) => fileOf[name.trim().split(/\s+as\s+/)[0]]))
        .filter(Boolean)
    const found = new Set()
    while (pending.length) {
        const file = pending.pop()
        if (found.has(file)) continue
        found.add(file)
        for (const [, relative] of read(file).matchAll(/import\s+\w+\s+from\s+"(\.{1,2}\/[^"]+\.vue)"/g)) {
            pending.push(path.resolve(path.dirname(file), relative))
        }
    }
    return [...found]
}

/** Element Plus component styles those design-system components load (`button`, `tooltip`...). */
const elementComponentsOf = (components) =>
    [...new Set(components.flatMap((file) => [...read(file).matchAll(/@use ['"]element-plus\/theme-chalk\/src\/([\w-]+)['"]/g)].map(([, name]) => name)))].sort()

/** The design-system token files, rescoped from the page to the graph. */
function tokenBlocks() {
    return Object.entries(TOKEN_FILES).flatMap(([file, {from, to}]) => parseBlocks(read(path.join(designSystemStyles, file))).map(({prelude, body}) => {
        if (prelude !== from) throw new Error(`${file}: expected "${from}", found "${prelude}"`)
        let scoped = body
        for (const [token, fallback] of Object.entries(HOST_TOKENS)) {
            scoped = scoped
                .replace(new RegExp(`^\\s*${token}\\s*:[^;]*;$`, "gm"), "")
                .replaceAll(`var(${token})`, `var(${token}, ${fallback})`)
        }
        return {prelude: to, body: scoped}
    }))
}

/** Keeps the rules whose `kel-` classes all belong to `used`; any rule outside that namespace is an error. */
function keepUsedRules(blocks, used) {
    const keepSelector = (selector) => {
        const classes = [...selector.matchAll(/\.kel-([\w-]+)/g)].map(([, cls]) => cls)
        if (classes.length) return classes.every((cls) => used.some((name) => cls.startsWith(name)))
        if (UNPREFIXED_DUPLICATES.test(selector)) return false
        if (PAGE_SELECTOR.test(selector)) return true
        throw new Error(`element-plus.scss emits a rule outside the kel namespace: "${selector}"`)
    }
    return blocks.flatMap(({prelude, body}) => {
        if (prelude.startsWith("@keyframes")) return [{prelude, body}]
        if (prelude.startsWith("@media")) {
            const inner = keepUsedRules(parseBlocks(body), used)
            return inner.length ? [{prelude, body: `\n${printBlocks(inner)}\n`}] : []
        }
        if (prelude.startsWith("@")) throw new Error(`element-plus.scss emits an unexpected at-rule: "${prelude}"`)
        const selectors = prelude.split(",").map((selector) => selector.trim()).filter(keepSelector)
        return selectors.length ? [{prelude: selectors.join(",\n"), body}] : []
    })
}

/** Drops the keyframes no remaining rule animates with. */
function keepUsedKeyframes(blocks) {
    const rules = printBlocks(blocks.filter(({prelude}) => !prelude.startsWith("@keyframes")))
    const animations = new Set([...rules.matchAll(/animation(?:-name)?\s*:\s*([^;]+);/g)].flatMap(([, value]) => value.split(/[\s,]+/)))
    return blocks.filter(({prelude}) => !prelude.startsWith("@keyframes") || animations.has(prelude.split(/\s+/)[1]))
}

/** Element Plus's base styles from the design system, limited to what the graph renders. */
function elementBlocks(used) {
    const blocks = parseBlocks(compileScssFile(path.join(designSystemStyles, "element-plus.scss")))
    return keepUsedKeyframes(keepUsedRules(blocks, used))
}

/** Icon sizing from vue-material-design-icons and the design system's global.css, scoped to the graph. */
function iconBlock() {
    const base = read(path.join(nodeModules, "vue-material-design-icons/styles.css"))
    const global = read(path.join(designSystemStyles, "global.css"))
    const sizing = global.slice(0, global.indexOf("/* global */")).replace("/* material-design-icon */", "")
    return {prelude: SCOPE, body: `\n${base.trim()}\n${sizing.trim()}\n`}
}

/** Removes the custom properties nothing reads, following `var()` references from `roots`. */
function pruneProperties(blocks, roots, prefixes) {
    const references = new Map()
    for (const [, name, value] of printBlocks(blocks).matchAll(/(--[\w-]+)\s*:\s*([^;{}]*);/g)) {
        const refs = references.get(name) ?? new Set()
        for (const [, ref] of value.matchAll(/var\(\s*(--[\w-]+)/g)) refs.add(ref)
        references.set(name, refs)
    }
    const reachable = new Set()
    const pending = [...references.keys()].filter((name) => roots.has(name) || prefixes.some((prefix) => name.startsWith(prefix)))
    while (pending.length) {
        const name = pending.pop()
        if (reachable.has(name)) continue
        reachable.add(name)
        pending.push(...(references.get(name) ?? []))
    }
    const isUnread = (line) => {
        const name = line.match(/^\s*(--[\w-]+)\s*:/)?.[1]
        return name !== undefined && !reachable.has(name)
    }
    return blocks
        .map(({prelude, body}) => ({prelude, body: body.split("\n").filter((line) => !isUnread(line) && line.trim()).join("\n")}))
        .filter(({body}) => body.trim())
        .map(({prelude, body}) => ({prelude, body: `\n${body}\n`}))
}

export function buildStandaloneCss() {
    const files = topologyFiles()
    const components = designSystemComponents(files)
    const elementComponents = elementComponentsOf(components)
    const tokens = tokenBlocks()
    const element = elementBlocks([...elementComponents, ...ELEMENT_INFRASTRUCTURE])
    const icons = iconBlock()

    const elementComponentCss = compileScss(["@use 'el-ns';", ...elementComponents.map((name) => `@use 'element-plus/theme-chalk/src/${name}';`)].join("\n"))
    const elementRules = element.filter(({prelude}) => !prelude.split(",").every((selector) => PAGE_SELECTOR.test(selector.trim())))
    const consumers = [...files, ...components].map(read).join("\n")
    const roots = new Set([...namesIn(consumers), ...namesIn(elementComponentCss), ...namesIn(printBlocks(elementRules)), ...namesIn(printBlocks([icons]))])
    const prune = (blocks) => printBlocks(pruneProperties(blocks, roots, dynamicPrefixesIn(consumers)))

    return [
        "/* Generated by scripts/generate-standalone-css.mjs from the design system. Do not edit. */",
        "/* design-system-disable-start: copies the palette, like the design system it comes from */",
        `/* Design-system tokens the graph reads, scoped to it */\n${prune(tokens)}`,
        `/* Element Plus base in the kel namespace, for the tooltips and menus inside the graph */\n${prune(element)}`,
        `/* Icon sizing */\n${printBlocks([icons])}`,
        "/* design-system-disable-end */",
    ].join("\n\n") + "\n"
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
    fs.writeFileSync(OUTPUT, buildStandaloneCss())
    console.log(`wrote ${path.relative(process.cwd(), OUTPUT)}`)
}
