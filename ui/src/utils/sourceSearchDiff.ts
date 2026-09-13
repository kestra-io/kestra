import type {SourceSearchResult as ApiSourceSearchResult, SourceMatch as ApiSourceMatch} from "@kestra-io/kestra-sdk"
import {crossSearchResultKey} from "./crossResourceSearch"

export type SourceMatch = Required<ApiSourceMatch>
export type SourceSearchResult = Required<Omit<ApiSourceSearchResult, "matches">> & {matches: SourceMatch[]}

export interface SourceSearchSelectionGroup {
    namespace: string;
    id: string;
    editable: boolean;
    matches: {line: number; column: number}[];
}

export interface SelectionSummary {
    selectedFlowCount: number;
    selectedMatchCount: number;
}

export function computeSelectionSummary(results: SourceSearchSelectionGroup[], selectedMatchKeys: Set<string>): SelectionSummary {
    let selectedFlowCount = 0
    let selectedMatchCount = 0

    for (const group of results) {
        if (!group.editable) {
            continue
        }
        const checkedCount = group.matches.filter((match) => selectedMatchKeys.has(crossSearchResultKey({
            type: "flows",
            namespace: group.namespace,
            id: group.id,
            line: match.line,
            column: match.column,
        }))).length
        if (checkedCount > 0) {
            selectedFlowCount += 1
            selectedMatchCount += checkedCount
        }
    }

    return {selectedFlowCount, selectedMatchCount}
}

export const SKIP_REASONS = ["READ_ONLY", "NOT_FOUND", "NO_MATCH", "NO_CHANGE", "INVALID_FLOW", "UNKNOWN"] as const

export type SkipReason = (typeof SKIP_REASONS)[number]

export function distinctSkipReasons(skipped: {reason?: string}[]): SkipReason[] {
    const present = new Set(skipped.map((flow) => flow.reason))
    const known = SKIP_REASONS.filter((reason) => present.has(reason))
    return known.length > 0 ? known : ["UNKNOWN"]
}

export interface ReplaceContext {
    query: string;
    replacement: string;
    regex: boolean;
    caseSensitive: boolean;
    wholeWord: boolean;
}

export function inlineReplacement(matched: string, context: ReplaceContext): string {
    if (!context.regex) {
        return context.replacement
    }
    try {
        const pattern = context.wholeWord ? `\\b(?:${context.query})\\b` : context.query
        return matched.replace(new RegExp(pattern, context.caseSensitive ? "" : "i"), context.replacement)
    } catch {
        return context.replacement
    }
}

export interface SourceSearchDiffMatch {
    line: number;
    before: string;
    after: string;
}

export interface DiffLine {
    kind: "context" | "removed" | "added";
    line: number;
    text: string;
}

export function buildDiffHunks(sourceLines: string[], matches: SourceSearchDiffMatch[], context = 2): DiffLine[] {
    if (matches.length === 0) {
        return []
    }

    const sorted = [...matches].sort((a, b) => a.line - b.line)

    const ranges = sorted.map((match) => ({
        start: Math.max(1, match.line - context),
        end: Math.min(sourceLines.length, match.line + context),
        matches: [match],
    }))

    const merged: typeof ranges = []
    for (const range of ranges) {
        const last = merged[merged.length - 1]
        if (last && range.start <= last.end + 1) {
            last.end = Math.max(last.end, range.end)
            last.matches.push(...range.matches)
        } else {
            merged.push(range)
        }
    }

    const lines: DiffLine[] = []
    for (const hunk of merged) {
        for (let line = hunk.start; line <= hunk.end; line++) {
            const match = hunk.matches.find((m) => m.line === line)
            if (match) {
                lines.push({kind: "removed", line, text: match.before})
                lines.push({kind: "added", line, text: match.after})
            } else {
                lines.push({kind: "context", line, text: sourceLines[line - 1] ?? ""})
            }
        }
    }

    return lines
}
