export function isOffsetInPebbleBlock(text: string, offset: number): boolean {
    if (offset < 2) {
        return false
    }
    const searchUpTo = offset - 1
    return text.lastIndexOf("{{", searchUpTo) > text.lastIndexOf("}}", searchUpTo)
}

/**
 * Tracks whether the cursor has *freshly entered* a Pebble `{{ }}` block, in a way that
 * survives the debounce used to throttle suggestion re-triggering on cursor moves.
 *
 * `track()` must be called on every cursor change (synchronously), never from inside the
 * debounced callback: a fast move out of and back into a block would otherwise be swallowed
 * by the debounce, leaving the latch stale ("still in pebble") so the re-entry transition is
 * missed and autocomplete never reopens. `consumeEntered()` is called once the cursor settles
 * and returns (then clears) whether a fresh entry happened during the burst.
 */
export function createPebbleEntryTracker() {
    let wasInPebbleBlock = false
    let enteredSinceConsume = false
    return {
        track(inPebbleBlock: boolean): void {
            if (inPebbleBlock && !wasInPebbleBlock) enteredSinceConsume = true
            wasInPebbleBlock = inPebbleBlock
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
