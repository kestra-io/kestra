// Types for `fingerprintRules.mjs`, which is plain JavaScript so the dependency-free PR gate can
// import it. Lets the TypeScript side use it without turning on `allowJs`.

/** Key separator. Chosen because `.` and `_` both occur inside real translation keys. */
export const KEY_SEPARATOR: string;

/** Flattens a nested translation object into `a|b|c` keys holding only string leaves. */
export function flattenStrings(value: unknown, prefix?: string): {[key: string]: string};

/** Short content hash of an English source string. Only ever compared for equality. */
export function fingerprintOf(englishValue: string): string;

/** Keys whose English text no longer matches what their translations were generated from. */
export function staleKeys(
    englishRoot: unknown,
    fingerprints: {[key: string]: string},
    keyPrefix?: string,
): string[];
