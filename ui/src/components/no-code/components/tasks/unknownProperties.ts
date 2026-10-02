const ALWAYS_KNOWN = new Set(["type"])

export function unknownPropertiesOf(
    model: unknown,
    properties: Record<string, unknown> | undefined,
    allowsAdditional: boolean,
): [string, unknown][] {
    if (allowsAdditional || typeof model !== "object" || model === null || Array.isArray(model)) return []
    const known = new Set(Object.keys(properties ?? {}))
    if (!known.size) return []
    return Object.entries(model as Record<string, unknown>).filter(([key, value]) =>
        !known.has(key) && !ALWAYS_KNOWN.has(key) && value !== undefined && value !== null)
}
