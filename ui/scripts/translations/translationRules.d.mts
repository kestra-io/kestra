// Types for `translationRules.mjs`. The implementation is plain JavaScript so the dependency-free
// PR gate can import it without a TypeScript loader; this file lets `compareTranslations.ts` import
// the same module without `allowJs`.

/** Every key path in the object, including the intermediate nodes. */
export function allKeys(obj: unknown, prefix?: string): string[];

/** Only the leaf key paths — the ones that actually carry a message. */
export function leafKeys(obj: unknown, prefix?: string): string[];

/** Leaf key path -> message, for every string leaf. */
export function flattenStrings(obj: unknown, prefix?: string, out?: Record<string, string>): Record<string, string>;

/** Placeholder names a message interpolates, deduplicated and sorted. */
export function placeholdersOf(message: string): string[];

/** Every way `message` breaks the placeholder rules, optionally compared against its English source. */
export function placeholderProblems(key: string, message: string, englishMessage?: string): string[];

export const NON_LATIN_LOCALE_SCRIPTS: Record<string, RegExp>;

export function untranslatedKeys(
    lang: string,
    messages: Record<string, string>,
    englishMessages: Record<string, string>,
): string[];
