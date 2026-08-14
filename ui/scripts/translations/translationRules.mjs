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

/**
 * Leaf key path -> message, for every string leaf.
 *
 * Arrays are descended into by index, matching the generator, which translates their elements
 * individually. Skipping them left every element of a list-valued key — `thinkingWords`, the
 * copilot's rotating status words — outside all three checks at once, so one untranslated entry
 * sat in the middle of a translated list with nothing able to see it.
 */
export function flattenStrings(obj, prefix = "", out = {}) {
    for (const key of Object.keys(obj ?? {})) {
        const value = obj[key]
        const fullKey = prefix ? `${prefix}.${key}` : key
        if (typeof value === "string") out[fullKey] = value
        else if (value !== null && typeof value === "object") flattenStrings(value, fullKey, out)
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

/**
 * Locales whose writing system is not Latin, with a pattern matching a character only that script
 * uses.
 *
 * These are the locales where "the value equals its English source" is proof that nothing was
 * translated. In a Latin-script locale it usually is not: German really does write "Status", and
 * Spanish really does write "Total", so flagging those would bury a real finding under false
 * positives. Restricting the check to these five keeps every hit actionable, at the cost of not
 * catching an untranslated German string whose English happens to be a German word too.
 */
export const NON_LATIN_LOCALE_SCRIPTS = {
    hi: /[ऀ-ॿ]/,
    ja: /[぀-ヿ一-鿿]/,
    ko: /[가-힯ᄀ-ᇿ]/,
    ru: /[Ѐ-ӿ]/,
    zh_CN: /[一-鿿]/,
}

/**
 * Words that stay in English in every locale, so a message built only from them is identical to its
 * English source on purpose.
 *
 * The first group is the generator's own reserved-term list - the prompt in
 * `./generateTranslations.ts` instructs the model to leave these in English, so a translation that
 * did exactly that must not be reported as missing. The rest are brand names, product names and
 * acronyms, which have no translation to begin with.
 */
const NEVER_TRANSLATED_WORDS = new Set([
    // Reserved terms, kept in sync with the prompt in ./generateTranslations.ts.
    "kv", "store", "namespace", "namespaces", "tenant", "tenants", "flow", "flows", "subflow",
    "subflows", "task", "tasks", "log", "logs", "blueprint", "blueprints", "id", "ids", "trigger",
    "triggers", "label", "labels", "key", "keys", "value", "values", "input", "inputs", "output",
    "outputs", "port", "ports", "worker", "workers", "backfill", "backfills", "healthcheck",
    "min", "max",
    // Terms of the same kind the model declines to translate in every locale, so an English value
    // for them is a deliberate choice rather than a skipped translation.
    "secret", "secrets", "token", "tokens", "payload", "payloads", "context", "email", "webhook",
    "webhooks", "true", "false",
    // Brands, product and format names.
    "kestra", "copilot", "github", "gitlab", "bitbucket", "slack", "docker", "kubernetes", "markdown",
    "terraform", "python", "java", "javascript", "node", "npm", "linux", "macos", "windows",
    "claude", "codex", "openai", "gemini", "anthropic", "cursor", "pebble", "elasticsearch",
    "kafka", "postgres", "mysql", "redis", "aws", "gcp", "azure",
    // Acronyms and units.
    "ai", "api", "bearer", "cli", "cpu", "csv", "gb", "http", "https", "iam", "iso", "jwt", "json",
    "jsonl", "kb", "k8s", "ldap", "mb", "mcp", "ms", "oauth", "oidc", "rbac", "regex", "rfc", "rsa",
    "s3", "saml", "scim", "sla", "slas", "sql", "sso", "ttl", "ui", "uri", "url", "utc", "uuid",
    "yaml", "yml",
    // Kestra's own entity names, which are product nouns in the UI just like `flow` and `namespace`.
    "app", "apps", "taskrun", "taskruns",
])

/**
 * Individual keys whose English text is the correct value in every locale, where no word-level rule
 * expresses why.
 *
 * Each entry is a string the translation model returns unchanged on every reroll, in every
 * non-Latin-script locale - its judgement being that the term is a Kestra product noun rather than
 * prose. Listing the key is honest about that; widening {@link NEVER_TRANSLATED_WORDS} with
 * "group", "queue" or "unchanged" would blind the check across hundreds of real messages to spare
 * these nine.
 */
const ALLOWED_ENGLISH_KEYS = new Set([
    // EE worker-group UI: entity names ("Worker Group", "Worker Queue", "Worker Instances") and the
    // reservation-mode name "Elastic", which sits alongside "Strict" and "Share".
    "cluster-worker-group",
    "worker-group",
    "worker-instances",
    "worker-group-capacity-bar-elastic-badge",
    "worker-group-subscription-mode-elastic",
    // vue-i18n plural form whose English carries no `{count}`; every reroll adds one, which the
    // placeholder check then rejects. Left in English until the English source is reworded.
    "worker-group-overview-kpi-workers-suffix",
    // Protocol and product terms: "Subject claim (sub)" is the OIDC claim name, "SCIM Provisioning"
    // and "Tenant Endpoint" name the feature and the field they configure.
    "credentials.subject_placeholder",
    "provisioning",
    "security.integration.uri",
    // A masked value plus its parenthetical, which the model keeps verbatim in every locale.
    "promote.modal.token_keep_placeholder",
    // Sample values for the notify recipe: a Slack channel name and an example email address,
    // which the model keeps verbatim on every reroll.
    "recipe.notify.slack_channel_placeholder",
    "recipe.notify.email_to_placeholder",
])

/**
 * True when English text is expected to survive translation verbatim because it is not prose:
 * config snippets, URLs, PEM headers, identifier-shaped placeholders, ALL-CAPS state labels (which
 * the generator's prompt also pins to English) and bare interpolations.
 */
const isNotProse = (message) => {
    const text = message.trim()
    return !/[a-zA-Z]/.test(text)                     // digits, punctuation or placeholders only
        || /^(https?:\/\/|www\.|\/|~\/|%)/.test(text) // URLs, paths, environment variables
        || /^-{3,}/.test(text)                        // PEM block headers
        || /^[a-z0-9]+([-_.][a-z0-9]+)+$/.test(text)  // slug-shaped sample values, e.g. `us-west-2`
        || /^[A-Z][A-Z0-9_]*$/.test(text)             // state labels, e.g. `FAILED`
        || /^\{[^{}]*\}$/.test(text)                  // a lone interpolation
        || /^<[^>]+>$/.test(text)                     // angle-bracket literals, e.g. `<tenant level quota>`
        || /^\S*=\S*$/.test(text)                     // query syntax samples, e.g. `filters[field][op]=value…`
        || /(^|\s)(~\/|%[A-Z_]+%)/.test(text)         // config file paths, e.g. `macOS: ~/Library/…`
        || /^[a-z][\w.]*(,\s*[a-z][\w.]*)+$/.test(text) // identifier lists, e.g. OAuth scopes `openid, profile, email`
}

/** The words in `message` that a translator would actually have had to translate. */
const translatableWords = (message) =>
    (message.replace(/\{[^{}]*\}/g, " ").replace(/<[^>]*>/g, " ").match(/[A-Za-z][A-Za-z']+/g) ?? [])
        .filter((word) => !NEVER_TRANSLATED_WORDS.has(word.toLowerCase()))

/**
 * Keys whose `lang` message is the untouched English text.
 *
 * `./generateTranslations.ts` falls back to the English source when a translation request fails, so
 * a failed run still writes a complete, well-formed, correctly-fingerprinted file - one that no
 * other check can tell apart from a translated one. Key parity passes, the message compiles, the
 * placeholders match and the fingerprint is current; only the value gives it away. Without this,
 * one bad run silently ships English to every locale and stays that way (kestra-io/kestra#18090).
 *
 * Returns an empty list for Latin-script locales, where an identical value is not evidence of
 * anything - see {@link NON_LATIN_LOCALE_SCRIPTS}.
 */
export function untranslatedKeys(lang, messages, englishMessages) {
    if (!(lang in NON_LATIN_LOCALE_SCRIPTS)) return []

    return Object.entries(messages)
        .filter(([key, message]) => {
            const english = englishMessages[key]
            return english !== undefined
                && message === english
                && !ALLOWED_ENGLISH_KEYS.has(key)
                && !isNotProse(english)
                && translatableWords(english).length > 0
        })
        .map(([key]) => key)
}
