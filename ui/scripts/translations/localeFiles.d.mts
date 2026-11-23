// Types for `localeFiles.mjs`, which stays plain JavaScript so the PR gate needs no TypeScript loader.

/** Parse a `*.locale.ts` default export into a plain object, throwing on anything but objects and strings. */
export function parseLocaleModule(source: string): Record<string, unknown>;
