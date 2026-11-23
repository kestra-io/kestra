/**
 * Returns true when `offset` (cursor position, between characters) sits inside an
 * unclosed Pebble block (`{{ ... }}` or `{% ... %}`) in `text`.
 *
 * The cursor at offset N sits between characters at index (N-1) and N, so we
 * search up to (N-1) — otherwise a closer that starts AT N (e.g. cursor placed
 * right before `}}` or `%}`) would be treated as closing the block, hiding autocomplete.
 */
export function isOffsetInPebbleBlock(text: string, offset: number): boolean {
    if (offset < 2) {
        return false
    }
    const searchUpTo = offset - 1
    const inPrint = text.lastIndexOf("{{", searchUpTo) > text.lastIndexOf("}}", searchUpTo)
    const inStmt = text.lastIndexOf("{%", searchUpTo) > text.lastIndexOf("%}", searchUpTo)
    return inPrint || inStmt
}
