export function isOffsetInPebbleBlock(text: string, offset: number): boolean {
    if (offset < 2) {
        return false
    }
    const searchUpTo = offset - 1
    return text.lastIndexOf("{{", searchUpTo) > text.lastIndexOf("}}", searchUpTo)
}

/**
 * Returns a stable key identifying the Pebble `{{ }}` block the cursor sits in — the offset
 * of the block's opening `{{` — or `null` when the cursor is not inside a block. Moving to a
 * different block yields a different key, which is how we distinguish "entered a new block"
 * from "still in the same block".
 */
export function pebbleBlockKeyAtOffset(text: string, offset: number): number | null {
    if (!isOffsetInPebbleBlock(text, offset)) {
        return null
    }
    const blockStart = text.lastIndexOf("{{", offset - 1)
    // The cursor must be past the opening `{{` (i.e. in the expression body), not wedged
    // between its two braces. `{|{}}` resolves to the same nearest `{{` as `{{|}}`, so without
    // this guard both yield the same key and moving back into the expression looks like
    // "never left" — the re-entry is missed and autocomplete never reopens.
    if (offset < blockStart + 2) {
        return null
    }
    return blockStart
}

/**
 * Tracks whether the cursor has *freshly entered* a Pebble `{{ }}` block, in a way that
 * survives the debounce used to throttle suggestion re-triggering on cursor moves.
 *
 * `track()` takes the block key (see `pebbleBlockKeyAtOffset`) and must be called on every
 * cursor change (synchronously), never from inside the debounced callback: a fast move out of
 * and back into a block would otherwise be swallowed by the debounce, leaving the latch stale
 * so the re-entry is missed and autocomplete never reopens. Tracking the block *key* rather
 * than a boolean also catches moving straight from one `{{ }}` block to another (no non-block
 * position in between), which a boolean in/out latch misses. Staying in the same block is not
 * a fresh entry, so dismissing the widget with Escape and nudging the cursor won't reopen it.
 * `consumeEntered()` is called once the cursor settles and returns (then clears) whether a
 * fresh entry happened during the burst.
 */
export function createPebbleEntryTracker() {
    let lastBlockKey: number | null = null
    let enteredSinceConsume = false
    return {
        track(blockKey: number | null): void {
            if (blockKey !== null && blockKey !== lastBlockKey) enteredSinceConsume = true
            lastBlockKey = blockKey
        },
        consumeEntered(): boolean {
            const entered = enteredSinceConsume
            enteredSinceConsume = false
            return entered
        },
    }
}

export const PEBBLE_SCHEMA_TYPES = ["flow", "dashboard", "app", "testsuites"] as const

export function isPebbleEnabled(opts: {
    pebble?: boolean
    lang?: string
    schemaType?: string
}): boolean {
    if (opts.pebble !== undefined) return opts.pebble
    if (opts.lang === "yaml-pebble") return true
    if ((PEBBLE_SCHEMA_TYPES as readonly string[]).includes(opts.schemaType ?? "")) return true
    return opts.lang === "yaml"
}
