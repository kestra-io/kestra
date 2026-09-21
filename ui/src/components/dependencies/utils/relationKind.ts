const KIND_TOKENS: Record<string, string> = {
    PRODUCES:    "--ks-dependencies-edge-produces",
    CONSUMED_BY: "--ks-dependencies-edge-consumed-by",
    UPSTREAM_OF: "--ks-dependencies-edge-upstream-of",
}

/** The relation kinds that reach the DAG today; order also drives the legend. */
export const RELATION_KINDS: string[] = Object.keys(KIND_TOKENS)

export const edgeKindToken = (kind?: string): string | undefined => (kind ? KIND_TOKENS[kind] : undefined)
