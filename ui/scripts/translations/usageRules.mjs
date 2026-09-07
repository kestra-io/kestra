// Where the source code uses a translation key, and which of those keys no locale defines.
//
// Plain JavaScript with no imports, like `translationRules.mjs` and for the same reason: the PR
// gate imports this before any `npm ci` has run.
//
// The checks in `translationRules.mjs` compare the locale files with each other, so a key that is
// missing from *every* locale - never added, or deleted in a cleanup while a component still used
// it - passes all of them and renders as its raw id in the UI. `credentials.delete_error` sat like
// that for months. The only way to see it is to read the source.
//
// Only literal keys are collected: `t("flows.create")`, `$t('yes')`, `` t(`demos.tests.title`) ``,
// `<i18n-t keypath="ai.copilot.title">`. Anything built at runtime - `t(e.message)`,
// `` t(`errors.${code}`) ``, `t("crud.type." + type)`, `:keypath="pill.keypath"` - is skipped:
// there is no way to know what it resolves to, and the point is zero false positives so a failure
// is always a real bug.

const EXTENSIONS = new Set([".vue", ".ts", ".js", ".mts", ".tsx"])

/**
 * A quoted literal passed straight to a translation call, followed by `,` or `)` so that a
 * concatenation (`t("errors." + code)`) is never mistaken for a complete key.
 *
 * The call shapes covered: `t(`, `$t(`, `te(`, `$te(`, `tm(`, `$tm(`, `rt(`, `i18n.global.t(` and any
 * `<receiver>.t(` where the receiver is not a plain identifier chain that ends in a non-i18n word
 * (kept broad on purpose: `ctx.t(...)` and `context.t(...)` are how the no-code and Monaco helpers
 * reach the translator). A bare `t(` must not be preceded by a word character, `$` or `.`, so
 * `format(`, `at(` and `foo.at(` do not count.
 *
 * Template literals are accepted only when they contain no `${`, in which case they are plain
 * strings that happen to use backticks.
 */
const CALL = /(?<![\w$])(?<fn>\$t[me]?|\$rt|(?<![.\w$])t[me]?|(?<![.\w$])rt|(?:i18n\.global|[\w$]+)\.t[me]?)\(\s*(?<quote>["'`])(?<key>(?:(?!\k<quote>)[^\\\n$]|\\.)+)\k<quote>\s*[,)]/g

/** `<i18n-t keypath="...">` - the static attribute only, never the bound `:keypath="expr"`. */
const KEYPATH = /(?<![:\w-])keypath=(?<quote>["'])(?<key>(?:(?!\k<quote>)[^\\\n$])+)\k<quote>/g

/** Files the scan reads: source files, minus tests, stories, type declarations and the locale files themselves. */
export function isScannedSourceFile(filePath) {
    const normalized = filePath.replaceAll("\\", "/")
    const base = normalized.slice(normalized.lastIndexOf("/") + 1)
    const extension = base.slice(base.lastIndexOf("."))
    if (!EXTENSIONS.has(extension)) return false
    if (/\.(spec|test|stories|d|locale)\.[cm]?[jt]sx?$/.test(base)) return false
    return !/\/(node_modules|dist|coverage|__tests__|__mocks__|translations)\//.test(normalized)
}

/**
 * Every literal translation key the source uses, with its 1-based line and whether the call is
 * an existence test (`te`), which tells the caller the key is allowed to be absent.
 */
export function translationKeyUsages(source) {
    const usages = []
    const collect = (regex, isGuard) => {
        for (const match of source.matchAll(regex)) {
            const key = match.groups.key
            // A trailing dot is a prefix about to be concatenated, never a complete key.
            if (key.length === 0 || key.endsWith(".")) continue
            const line = source.slice(0, match.index).split("\n").length
            usages.push({key, line, guarded: isGuard(match)})
        }
    }
    collect(CALL, (match) => /(?:^|\.|\$)te$/.test(match.groups.fn))
    collect(KEYPATH, () => false)
    return usages
}

/**
 * The usages whose key exists at no level of any of the given key sets.
 *
 * `definedKeys` are the paths `allKeys` produces - leaves and namespaces alike. A namespace counts
 * as defined because `$t("change state hint")[status]` style lookups read an object node on
 * purpose, and vue-i18n resolves a namespace to a string as well. Keys touched anywhere by `te()`
 * are dropped: the code has already made its peace with them being absent.
 */
export function undefinedKeyUsages(usagesByFile, definedKeys) {
    const guarded = new Set()
    for (const usages of Object.values(usagesByFile)) {
        for (const {key, guarded: isGuarded} of usages) {
            if (isGuarded) guarded.add(key)
        }
    }

    const findings = []
    for (const [file, usages] of Object.entries(usagesByFile)) {
        for (const {key, line} of usages) {
            if (guarded.has(key) || definedKeys.has(key)) continue
            findings.push({file, line, key})
        }
    }
    return findings
}
