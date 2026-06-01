export function isOffsetInPebbleBlock(text: string, offset: number): boolean {
    if (offset < 2) {
        return false
    }
    const searchUpTo = offset - 1
    return text.lastIndexOf("{{", searchUpTo) > text.lastIndexOf("}}", searchUpTo)
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
