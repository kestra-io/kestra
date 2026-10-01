import {isAlias, isMap, isPair, isScalar, isSeq, LineCounter, parseDocument, visit, type Node, type Pair} from "yaml"

import {pointerKeys, pointerSegments, type ValidationError} from "./validationErrors"

export interface LineRange {
    startLineNumber: number;
    startColumn: number;
    endLineNumber: number;
    endColumn: number;
}

/** One violation: the first line carries the marker, the others only the squiggle. */
export interface ViolationMarker {
    message: string;
    lines: LineRange[];
}

type Range = [number, number]

/** An existing key is marked with its scalar value, or alone when it holds a block. */
function pairTargets(pair: Pair): Node[] {
    return [pair.key as Node, ...(isScalar(pair.value) ? [pair.value] : [])]
}

interface Location {
    targets: Node[];
    /** The key the path names but the source lacks, so the marker sits on its enclosing block. */
    missing?: string;
    /** The last key on the path, marked when the targets hold no scalar (an alias, an out-of-range item). */
    fallback?: Node;
}

/** A path that stops short of its leaf, a missing key, marks the deepest node that exists, except the document root. */
function locate(root: Node | null, segments: string[]): Location {
    let node: Node | null | undefined = root
    let fallback: Node | undefined
    for (const [depth, segment] of segments.entries()) {
        const isLeaf = depth === segments.length - 1
        const missing = {targets: depth === 0 || !node ? [] : [node], missing: segment, fallback}
        if (isMap(node)) {
            const keys = pointerKeys(segment)
            const pair = node.items.find(item => isScalar(item.key) && keys.includes(String(item.key.value)))
            if (!pair) return missing
            if (isLeaf && isPair(pair)) return {targets: pairTargets(pair)}
            fallback = pair.key as Node
            node = pair.value as Node | null
        } else if (isSeq(node)) {
            const item = node.items[Number(segment)] as Node | undefined
            if (!item) return missing
            node = item
        } else {
            return missing
        }
    }
    return {targets: segments.length === 0 || !node ? [] : [node], fallback}
}

/** A multi-line scalar, such as a `|` script, is marked on its first line only. */
function firstLine(source: string, [start, end]: [number, number, number]): Range {
    const newline = source.indexOf("\n", start)
    return [start, newline === -1 ? end : Math.min(end, newline)]
}

function scalarRanges(source: string, targets: Node[]): Range[] {
    const ranges: Range[] = []
    for (const target of targets) {
        if (isScalar(target) || isAlias(target)) {
            if (target.range) ranges.push(firstLine(source, target.range))
            continue
        }
        // A block's own keys and values only: nested tasks are not what is missing a key.
        visit(target, {
            Scalar(_, scalar) {
                if (scalar.range) ranges.push(firstLine(source, scalar.range))
            },
            Map(_, map) {
                if (map !== target) return visit.SKIP
            },
            Seq(_, seq) {
                if (seq !== target) return visit.SKIP
            },
        })
    }
    return ranges
}

/** Indentation, dashes and comments stay unmarked: each line spans its first to its last key or value. */
function lineSpans(source: string, ranges: Range[]): Range[] {
    const spans = new Map<number, Range>()
    for (const [start, end] of ranges) {
        let lineStart = source.lastIndexOf("\n", start - 1) + 1
        while (lineStart < end) {
            const newline = source.indexOf("\n", lineStart)
            const lineEnd = newline === -1 ? source.length : newline
            let from = Math.max(start, lineStart)
            let to = Math.min(end, lineEnd)
            while (from < to && /\s/.test(source[from])) from++
            while (to > from && /\s/.test(source[to - 1])) to--
            if (from < to) {
                const span = spans.get(lineStart)
                spans.set(lineStart, span ? [Math.min(span[0], from), Math.max(span[1], to)] : [from, to])
            }
            lineStart = lineEnd + 1
        }
    }
    return [...spans.values()]
}

export function violationMarkers(source: string, errors: ValidationError[] | undefined): ViolationMarker[] {
    const located = errors?.filter(error => error.pointer && error.detail) ?? []
    if (!located.length) return []
    const lineCounter = new LineCounter()
    // Duplicate keys are reported by the editor itself and must not hide these markers.
    const doc = parseDocument(source, {lineCounter, uniqueKeys: false})
    if (doc.errors.length) return []

    return located.flatMap(error => {
        const {targets, missing, fallback} = locate(doc.contents, pointerSegments(error.pointer!))
        const message = missing && !/^\d+$/.test(missing) ? `${pointerKeys(missing).at(-1)}: ${error.detail}` : error.detail!
        let ranges = scalarRanges(source, targets)
        if (!ranges.length && fallback) ranges = scalarRanges(source, [fallback])
        const lines = lineSpans(source, ranges).map(([start, end]) => {
            const from = lineCounter.linePos(start)
            const to = lineCounter.linePos(end)
            return {startLineNumber: from.line, startColumn: from.col, endLineNumber: to.line, endColumn: to.col}
        })
        return lines.length ? [{message, lines}] : []
    })
}
