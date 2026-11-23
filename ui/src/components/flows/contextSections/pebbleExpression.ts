// Pebble's lexer only recognizes `\'` as an escape (an escaped quote); it has no `\\` -> `\` rule of
// its own, so doubling backslashes here would leave a literal extra backslash in the parsed value.
//
// This does not round-trip a value ending in a backslash, or one with a backslash immediately before
// a quote (the trailing `\` would escape the quote we append, running the literal on unterminated) —
// unreachable for the three current callers, so left undone rather than added speculatively: KV keys
// are validated server-side against `[A-Za-z0-9][A-Za-z0-9._-]*` (KVStore.KEY_VALIDATOR_PATTERN),
// secret names are env-var-derived, and a namespace file's path is normalized through
// NamespaceFile.filePath() -> toLogicalPath(), which replaces every backslash with `/` before it's
// ever returned to the client.
export function escapePebbleLiteral(value: string): string {
    return value.replace(/'/g, "\\'")
}
