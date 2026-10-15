/**
 * The shipped locales, in one place.
 *
 * This list used to be restated four times — the generator's `[code, language]` pairs, the
 * checker's codes, the app's `SUPPORT_LOCALES`, and one hand-written wrapper file per language in
 * EE — so adding a locale meant finding all four and creating a fifth file. Everything derives
 * from here instead.
 *
 * It lives in `src/` rather than beside the tooling because the app imports it at runtime; the
 * Node-only scripts are free to import app data, but not the other way round.
 *
 * The English name doubles as the target language handed to the translation model, which is why it
 * reads "Simplified Chinese (Mandarin)" rather than a bare endonym.
 */
export const LANGUAGES: ReadonlyArray<readonly [string, string]> = [
    ["de", "German"],
    ["es", "Spanish"],
    ["fr", "French"],
    ["hi", "Hindi"],
    ["it", "Italian"],
    ["ja", "Japanese"],
    ["ko", "Korean"],
    ["pl", "Polish"],
    ["pt", "Portuguese"],
    ["pt_BR", "Portuguese (Brazil)"],
    ["ru", "Russian"],
    ["zh_CN", "Simplified Chinese (Mandarin)"],
]

/** Locale codes that are translated. English is the reference and is never among them. */
export const TRANSLATED_LOCALES = LANGUAGES.map(([code]) => code)

/** Every locale the app can run in, English included. */
export const SUPPORT_LOCALES = ["en", ...TRANSLATED_LOCALES] as const
