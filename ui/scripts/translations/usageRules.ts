/**
 * Where the source uses a translation key, which used keys no locale defines, and which defined keys
 * no source can reach. Dependency-free: the PR gate imports this before any `npm ci` has run, which
 * Node does for TypeScript too as long as the syntax stays erasable.
 *
 * @see ./README.md for what the gate does with all of this
 */

const SOURCE_EXTENSIONS = new Set([".vue", ".ts", ".js", ".mts", ".tsx"])

const MAX_KEY_LENGTH = 120

/**
 * A quoted literal passed straight to a translation call, followed by `,` or `)` so that
 * `t("errors." + code)` is never mistaken for a complete key. The receiver is kept broad on purpose,
 * since `ctx.t(...)` is how the no-code and Monaco helpers reach the translator; a bare `t(` may not
 * follow a word character, `$` or `.`, so `format(`, `at(` and `foo.at(` do not count.
 */
const CALL = /(?<![\w$])(?<fn>\$t[me]?|\$rt|(?<![.\w$])t[me]?|(?<![.\w$])rt|(?:i18n\.global|[\w$]+)\.t[me]?)\(\s*(?<quote>["'`])(?<key>(?:(?!\k<quote>)[^\\\n$]|\\.)+)\k<quote>\s*[,)]/g

/** A key prefix completed at runtime, `t("crud.type." + type)`: everything up to the last dot has to exist. */
const PREFIX = /(?<![\w$])(?:\$t[me]?|\$rt|(?<![.\w$])t[me]?|(?<![.\w$])rt|(?:i18n\.global|[\w$]+)\.t[me]?)\(\s*(?:(?<quote>["'])(?<literal>(?:(?!\k<quote>)[^\\\n$])*)\k<quote>\s*\+|`(?<template>[^`$]*)\$\{)/g

/** The static `<i18n-t keypath="...">` attribute only, never the bound `:keypath="expr"`. */
const KEYPATH = /(?<![:\w-])keypath=(?<quote>["'])(?<key>(?:(?!\k<quote>)[^\\\n$])+)\k<quote>/g

/** `i18n-keys: a, b, c`: the keys a call whose key is a variable can produce, declared where that value comes from. */
const DECLARED_KEYS = /i18n-keys:(?<keys>[^\n]+)/g

const COMMENT_CLOSER = /\s*(?:-->|\*\/)\s*$/

/**
 * Quoted strings, each style on its own pass: `:keypath="a ? 'x.y' : 'x.z'"` is one double-quoted
 * string hiding two single-quoted keys, and a single alternation would keep only the outer one.
 */
const DOUBLE_QUOTED = /"((?:[^"\\\n]|\\.)*)"/g
const SINGLE_QUOTED = /'((?:[^'\\\n]|\\.)*)'/g
const BACKTICK = /`((?:[^`\\]|\\.)*)`/g

const STRING_CONSTANT = /\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(["'`])((?:(?!\2)[^\\\n$])*)\2/g

const INTERPOLATION = /\$\{([A-Za-z_$][\w$]*)\}/g

const REGEX_SPECIAL = /[.*+?^${}()|[\]\\]/g

export type KeyUsage = {key: string; line: number; guarded: boolean}

export type NamespaceUsage = {namespace: string; line: number}

/** Everything the scanned source offers that a defined key can be reached through. */
export type KeyEvidence = {
    keys: Set<string>
    namespaces: Set<string>
    strings: Set<string>
    patterns: RegExp[]
}

const lineOf = (source: string, index: number): number => source.slice(0, index).split("\n").length

export function isScannedSourceFile(filePath: string): boolean {
    const normalized = filePath.replaceAll("\\", "/")
    const base = normalized.slice(normalized.lastIndexOf("/") + 1)

    if (!SOURCE_EXTENSIONS.has(base.slice(base.lastIndexOf(".")))) return false
    if (/\.(spec|test|stories|d|locale)\.[cm]?[jt]sx?$/.test(base)) return false

    return !/\/(node_modules|dist|coverage|__tests__|__mocks__|translations)\//.test(normalized)
}

/**
 * Every translation key the source names, with its 1-based line and whether the call is an existence
 * test (`te`), which tells the caller the key is allowed to be absent.
 */
export function translationKeyUsages(source: string): KeyUsage[] {
    const usages: KeyUsage[] = []

    for (const [pattern, isGuard] of [
        [CALL, (fn: string) => /(?:^|\.|\$)te$/.test(fn)],
        [KEYPATH, () => false],
    ] as const) {
        for (const match of source.matchAll(pattern)) {
            const key = match.groups!.key
            // A trailing dot is a prefix about to be concatenated, never a complete key.
            if (key.length === 0 || key.endsWith(".")) continue
            usages.push({key, line: lineOf(source, match.index), guarded: isGuard(match.groups!.fn)})
        }
    }

    for (const match of source.matchAll(DECLARED_KEYS)) {
        const line = lineOf(source, match.index)
        for (const key of match.groups!.keys.replace(COMMENT_CLOSER, "").split(",")) {
            if (key.trim()) usages.push({key: key.trim(), line, guarded: false})
        }
    }

    return usages
}

/** Every namespace the source completes at runtime. A prefix with no dot names none and is skipped. */
export function translationNamespaceUsages(source: string): NamespaceUsage[] {
    const usages: NamespaceUsage[] = []

    for (const match of source.matchAll(PREFIX)) {
        const prefix = match.groups!.literal ?? match.groups!.template
        const lastDot = prefix.lastIndexOf(".")
        if (lastDot > 0) usages.push({namespace: prefix.slice(0, lastDot), line: lineOf(source, match.index)})
    }

    return usages
}

/** The runtime-completed namespaces no key set defines: every key built under them renders as its raw id. */
export function undefinedNamespaceUsages(usagesByFile: Record<string, NamespaceUsage[]>, definedKeys: Set<string>) {
    return Object.entries(usagesByFile).flatMap(([file, usages]) =>
        usages.filter(({namespace}) => !definedKeys.has(namespace)).map(({namespace, line}) => ({file, line, namespace})),
    )
}

/**
 * The usages whose key exists at no level of any key set. A namespace counts as defined, because
 * `$t("change state hint")[status]` reads an object node on purpose; a key `te()` tests anywhere is
 * dropped, because the code has already made its peace with it being absent.
 */
export function undefinedKeyUsages(usagesByFile: Record<string, KeyUsage[]>, definedKeys: Set<string>) {
    const guarded = new Set(
        Object.values(usagesByFile).flatMap((usages) => usages.filter(({guarded: isGuarded}) => isGuarded).map(({key}) => key)),
    )

    return Object.entries(usagesByFile).flatMap(([file, usages]) =>
        usages.filter(({key}) => !guarded.has(key) && !definedKeys.has(key)).map(({key, line}) => ({file, line, key})),
    )
}

const escapeRegex = (text: string): string => text.replace(REGEX_SPECIAL, "\\$&")

/** Resolves `${THEME}.confirmations.color_mode` when THEME is a string constant of the same file. */
const inlineConstants = (body: string, constants: Map<string, string>): string =>
    body.replace(INTERPOLATION, (expression, name: string) => constants.get(name) ?? expression)

/**
 * `bulk ${action} backfills` becomes /^bulk .+ backfills$/. A key opens with its namespace, so a
 * template opening with an expression is an id or a URL, not a key, and would match half the
 * dictionary if turned into a pattern.
 */
function templatePattern(body: string): RegExp | undefined {
    if (body.indexOf("${") < 2) return undefined

    let pattern = ""
    let index = 0
    while (index < body.length) {
        const start = body.indexOf("${", index)
        if (start === -1) {
            pattern += escapeRegex(body.slice(index))
            break
        }

        pattern += `${escapeRegex(body.slice(index, start))}.+`
        let depth = 1
        index = start + 2
        while (index < body.length && depth > 0) {
            if (body[index] === "{") depth++
            else if (body[index] === "}") depth--
            index++
        }
    }

    return new RegExp(`^${pattern}$`)
}

export const emptyEvidence = (): KeyEvidence => ({keys: new Set(), namespaces: new Set(), strings: new Set(), patterns: []})

/**
 * Everything in one file that can keep a defined key alive, added to `evidence`. Every quoted string
 * counts, since a key travels through data (`labelKey: "setup.survey.company_1_10"`, a route
 * `meta.title`) before `t(variable)` reads it, and a string ending in a dot is a namespace completed
 * elsewhere.
 */
export function collectKeyEvidence(source: string, evidence: KeyEvidence = emptyEvidence()): KeyEvidence {
    for (const {key} of translationKeyUsages(source)) evidence.keys.add(key)
    for (const {namespace} of translationNamespaceUsages(source)) evidence.namespaces.add(namespace)

    for (const pattern of [DOUBLE_QUOTED, SINGLE_QUOTED]) {
        for (const [, text] of source.matchAll(pattern)) {
            if (text.length === 0 || text.length > MAX_KEY_LENGTH) continue
            evidence.strings.add(text)
            if (text.length > 1 && text.endsWith(".")) evidence.namespaces.add(text.slice(0, -1))
        }
    }

    const constants = new Map([...source.matchAll(STRING_CONSTANT)].map(([, name, , value]) => [name, value]))
    for (const [, raw] of source.matchAll(BACKTICK)) {
        if (raw.includes("\n") || raw.length > 4 * MAX_KEY_LENGTH) continue

        const body = inlineConstants(raw, constants)
        if (body.includes("${")) {
            const pattern = templatePattern(body)
            if (pattern) evidence.patterns.push(pattern)
        } else if (body.length > 0 && body.length <= MAX_KEY_LENGTH) {
            evidence.strings.add(body)
        }
    }

    return evidence
}

const ancestorsOf = (key: string): string[] => {
    const parts = key.split(".")
    return parts.slice(0, -1).map((_, index) => parts.slice(0, index + 1).join("."))
}

/** The leaf keys no evidence reaches, directly or through an ancestor, a namespace or a template pattern. */
export function unusedDefinedKeys(leafKeys: string[], evidence: KeyEvidence): string[] {
    const named = (key: string) => evidence.keys.has(key) || evidence.strings.has(key)
    const reached = (key: string) => named(key) || evidence.namespaces.has(key)

    return leafKeys.filter((key) =>
        !named(key) && !ancestorsOf(key).some(reached) && !evidence.patterns.some((pattern) => pattern.test(key)),
    )
}
