/**
 * The flow's own identity, as both authoring surfaces show it: the No-code flow block and the
 * Topology canvas chip. Labels reach us in two shapes, so the reading lives here rather than in
 * whichever component happened to need it first.
 */
export function flowDescriptionOf(parsed: Record<string, unknown> | undefined): string | undefined {
    const description = parsed?.description
    return typeof description === "string" ? description : undefined
}

export function flowLabelEntriesOf(parsed: Record<string, unknown> | undefined): [string, string][] {
    const labels = parsed?.labels
    if (Array.isArray(labels)) {
        return labels
            .filter((label): label is {key: string; value: unknown} =>
                Boolean(label) && typeof label === "object" && "key" in label)
            .map((label) => [String(label.key), String(label.value ?? "")])
    }
    if (labels && typeof labels === "object") {
        return Object.entries(labels).map(([key, value]) => [key, String(value ?? "")])
    }
    return []
}
