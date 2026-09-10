/**
 * Client-side line-level diff for previewing a proposed YAML/source change before it's applied
 * (AI Copilot: proposed-action diff, draft-apply confirm). No diff library exists in the repo
 * (`ui/src/utils/sourceSearchDiff.ts` only builds hunks around already-known match positions), and the
 * inputs here are flow/dashboard YAML — at most a few hundred lines — so a small LCS-based diff avoids
 * pulling in a dependency for something this bounded.
 */

export interface TextDiffLine {
    kind: "context" | "removed" | "added"
    text: string
}

export interface TextDiffGap {
    kind: "gap"
    count: number
}

export type TextDiffEntry = TextDiffLine | TextDiffGap

export interface TextDiffSummary {
    added: number
    removed: number
}

// Above this many (old lines × new lines) cells, the O(n·m) LCS table is skipped in favor of a full
// replacement. Flow/dashboard YAML realistically runs to a few hundred lines, so this only guards
// against pathological input (e.g. a huge pasted file) turning a UI preview into a slow computation.
const MAX_LCS_CELLS = 4_000_000

const DEFAULT_CONTEXT_LINES = 3

/** Line-level diff between two texts, computed via the classic LCS-backtrack algorithm (same idea as
 *  `git diff`/`diff(1)`, without collapsing unchanged runs — see `collapseContext` for that). */
export function diffLines(oldText: string, newText: string): TextDiffLine[] {
    if (oldText === newText) {
        return splitLines(oldText).map((text) => ({kind: "context", text}))
    }

    const oldLines = splitLines(oldText)
    const newLines = splitLines(newText)

    if (oldLines.length * newLines.length > MAX_LCS_CELLS) {
        return [
            ...oldLines.map((text) => ({kind: "removed" as const, text})),
            ...newLines.map((text) => ({kind: "added" as const, text})),
        ]
    }

    return lcsDiff(oldLines, newLines)
}

export function summarizeDiff(entries: TextDiffLine[]): TextDiffSummary {
    let added = 0
    let removed = 0
    for (const entry of entries) {
        if (entry.kind === "added") added++
        else if (entry.kind === "removed") removed++
    }
    return {added, removed}
}

/** Collapses long unchanged runs down to `context` lines on each side of a change, like a unified
 *  diff's hunk context, so a large file with a small edit doesn't render every untouched line. */
export function collapseContext(entries: TextDiffLine[], context = DEFAULT_CONTEXT_LINES): TextDiffEntry[] {
    const result: TextDiffEntry[] = []
    let i = 0
    while (i < entries.length) {
        if (entries[i].kind !== "context") {
            result.push(entries[i])
            i++
            continue
        }
        let j = i
        while (j < entries.length && entries[j].kind === "context") j++
        const run = entries.slice(i, j) as TextDiffLine[]
        const leading = i === 0 ? 0 : context
        const trailing = j === entries.length ? 0 : context
        if (run.length <= leading + trailing) {
            result.push(...run)
        } else {
            if (leading > 0) result.push(...run.slice(0, leading))
            result.push({kind: "gap", count: run.length - leading - trailing})
            if (trailing > 0) result.push(...run.slice(run.length - trailing))
        }
        i = j
    }
    return result
}

function splitLines(text: string): string[] {
    return text.length ? text.split("\n") : []
}

function lcsDiff(oldLines: string[], newLines: string[]): TextDiffLine[] {
    const n = oldLines.length
    const m = newLines.length
    // lengths[i][j] = length of the LCS of oldLines[i:] and newLines[j:].
    const lengths: number[][] = Array.from({length: n + 1}, () => new Array<number>(m + 1).fill(0))
    for (let i = n - 1; i >= 0; i--) {
        for (let j = m - 1; j >= 0; j--) {
            lengths[i][j] = oldLines[i] === newLines[j]
                ? lengths[i + 1][j + 1] + 1
                : Math.max(lengths[i + 1][j], lengths[i][j + 1])
        }
    }

    const entries: TextDiffLine[] = []
    let i = 0
    let j = 0
    while (i < n && j < m) {
        if (oldLines[i] === newLines[j]) {
            entries.push({kind: "context", text: oldLines[i]})
            i++
            j++
        } else if (lengths[i + 1][j] >= lengths[i][j + 1]) {
            entries.push({kind: "removed", text: oldLines[i]})
            i++
        } else {
            entries.push({kind: "added", text: newLines[j]})
            j++
        }
    }
    while (i < n) {
        entries.push({kind: "removed", text: oldLines[i]})
        i++
    }
    while (j < m) {
        entries.push({kind: "added", text: newLines[j]})
        j++
    }
    return entries
}
