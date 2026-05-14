/**
 * Returns true when `offset` (cursor position, between characters) sits inside an
 * unclosed `{{ ... }}` pebble block in `text`.
 *
 * The cursor at offset N sits between characters at index (N-1) and N, so we
 * search up to (N-1) — otherwise a `}}` that starts AT N (e.g. cursor placed
 * right before `}}` of an empty `{{ }}` block) would be treated as closing the
 * block, hiding autocomplete.
 */
export function isOffsetInPebbleBlock(text: string, offset: number): boolean {
    // Cursor at offset 0 sits before everything — nothing can have opened.
    // For offset >= 2, search up to (offset - 1) so a `}}` that starts AT the
    // cursor is not treated as a closer.
    if (offset < 2) {
        return false
    }
    const searchUpTo = offset - 1
    return text.lastIndexOf("{{", searchUpTo) > text.lastIndexOf("}}", searchUpTo)
}
