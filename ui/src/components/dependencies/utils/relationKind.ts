const KIND_TOKENS: Record<string, string> = {
    PRODUCES:    "--ks-dependencies-edge-produces",
    CONSUMED_BY: "--ks-dependencies-edge-consumed-by",
    UPSTREAM_OF: "--ks-dependencies-edge-upstream-of",
    PART_OF:     "--ks-dependencies-edge-part-of",
    RELATED:     "--ks-dependencies-edge-related",
    DEPENDS_ON:  "--ks-dependencies-edge-depends-on",
}

/** The relation kinds that reach the dependency graph today; order also drives the legend. */
export const RELATION_KINDS: string[] = Object.keys(KIND_TOKENS)

export const edgeKindToken = (kind?: string): string | undefined => (kind ? KIND_TOKENS[kind] : undefined)

/** PART_OF, RELATED and DEPENDS_ON join two assets without describing a data-flow step, so a DAG rank walk must skip them. */
const NON_LINEAGE_KINDS = new Set(["PART_OF", "RELATED", "DEPENDS_ON"])

/** True for a lineage edge or a kindless one (Flow/Namespace/Execution views never carry a kind). */
export const isLineageEdge = (kind?: string): boolean => !kind || !NON_LINEAGE_KINDS.has(kind)
