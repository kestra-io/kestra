// The translation rules both checkers enforce, in one place.
//
// Plain JavaScript with no imports, deliberately: `check-translations.mjs` is the PR gate and has
// to run straight after `actions/checkout`, before any `npm ci`. A single third-party import here
// would break that — and did, when the gate went through `compareTranslations.ts` and failed with
// "Cannot find module '@intlify/message-compiler'" on a sparse checkout.
//
// `compareTranslations.ts` layers the one thing that genuinely needs a dependency on top: running
// each message through vue-i18n's real compiler. Everything below is shared.
//
// Note on separators: these key paths join with `.`, matching how a developer writes `t("a.b.c")`,
// because they end up in error messages a human reads. Fingerprints join with `|` instead — see
// `fingerprints.ts` for why. The two are not interchangeable, and mixing them up once already
// caused 1,249 healthy keys to be reported stale.

/** Every key path in the object, including the intermediate nodes. */
export function allKeys(obj, prefix = "") {
    return Object.keys(obj ?? {}).reduce((keys, key) => {
        const fullKey = prefix ? `${prefix}.${key}` : key
        keys.push(fullKey)
        const value = obj[key]
        if (value !== null && typeof value === "object" && !Array.isArray(value)) {
            keys.push(...allKeys(value, fullKey))
        }
        return keys
    }, [])
}

/** Only the leaf key paths — the ones that actually carry a message. */
export function leafKeys(obj, prefix = "") {
    return Object.keys(obj ?? {}).reduce((keys, key) => {
        const fullKey = prefix ? `${prefix}.${key}` : key
        const value = obj[key]
        if (value !== null && typeof value === "object" && !Array.isArray(value)) {
            return keys.concat(leafKeys(value, fullKey))
        }
        keys.push(fullKey)
        return keys
    }, [])
}

/** Leaf key path -> message, for every string leaf. */
export function flattenStrings(obj, prefix = "", out = {}) {
    for (const key of Object.keys(obj ?? {})) {
        const value = obj[key]
        const fullKey = prefix ? `${prefix}.${key}` : key
        if (typeof value === "string") out[fullKey] = value
        else if (value !== null && typeof value === "object" && !Array.isArray(value)) {
            flattenStrings(value, fullKey, out)
        }
    }
    return out
}

// --- Interpolation placeholders -------------------------------------------
// vue-i18n interpolates a SINGLE pair of braces (`{name}` / `{0}`). Each failure mode below is a
// compile error, so `t()` throws and the component rendering the key fails outright rather than
// degrading to literal text.

/**
 * Strip the two ways a brace can appear as literal text rather than interpolation:
 * `\{` / `\}` backslash escapes (e.g. `\{ 1 key \}`) and `{'...'}` literal blocks.
 */
const stripLiteralEscapes = (message) => message.replace(/\\[{}]/g, "").replace(/\{'[^']*'\}/g, "")

/** Named placeholders are identifiers; list placeholders are indices. */
const VALID_PLACEHOLDER = /^(?:[A-Za-z_$][\w$]*|\d+)$/

/** Deduplicated, so a locale collapsing `{count} x | {count} xs` to one plural form still matches. */
export function placeholdersOf(message) {
    const found = [...stripLiteralEscapes(message).matchAll(/\{\s*([^{}\s][^{}]*?)\s*\}/g)].map((m) => m[1])
    return [...new Set(found)].sort()
}

/** Every way `message` breaks the placeholder rules, optionally compared against its English source. */
export function placeholderProblems(key, message, englishMessage) {
    const problems = []

    if (/\{\{[^{}]*\}\}/.test(stripLiteralEscapes(message))) {
        problems.push(`"${key}" uses double braces — vue-i18n rejects \`{{name}}\` with "Not allowed nest placeholder"; write \`{name}\``)
    }

    for (const name of placeholdersOf(message)) {
        if (!VALID_PLACEHOLDER.test(name)) {
            problems.push(`"${key}" has an invalid placeholder \`{${name}}\` — the name must be an identifier or an index, and must never be translated`)
        }
    }

    if (englishMessage !== undefined) {
        const expected = placeholdersOf(englishMessage)
        const actual = placeholdersOf(message)
        const same = expected.length === actual.length && expected.every((name, i) => name === actual[i])
        if (!same) {
            const show = (list) => (list.length ? list.map((n) => `{${n}}`).join(", ") : "none")
            problems.push(`"${key}" interpolates ${show(actual)} but its English source declares ${show(expected)} — an invented placeholder renders as an empty gap, a dropped one loses the value`)
        }
    }

    return problems
}
