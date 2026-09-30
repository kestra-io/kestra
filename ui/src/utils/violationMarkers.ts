import {isMap, isPair, isScalar, isSeq, LineCounter, parseDocument, visit, type Node, type Pair} from "yaml"

import type {ValidationError} from "./validationErrors"

export interface ViolationMarker {
    message: string;
    startLineNumber: number;
    startColumn: number;
    endLineNumber: number;
    endColumn: number;
}

type Range = [number, number]

function pointerSegments(pointer: string): string[] {
    return pointer.split("/").slice(1).map(segment => segment.replace(/~1/g, "/").replace(/~0/g, "~"))
}

/** An existing key is marked with its scalar value, or alone when it holds a block. */
function pairTargets(pair: Pair): Node[] {
    return [pair.key as Node, ...(isScalar(pair.value) ? [pair.value] : [])]
}

interface Location {
    targets: Node[];
    /** The key the path names but the source lacks, so the marker sits on its enclosing block. */
    missing?: string;
}

/** A path that stops short of its leaf, a missing key, marks the deepest node that exists, except the document root. */
function locate(root: Node | null, segments: string[]): Location {
    let node: Node | null | undefined = root
    for (const [depth, segment] of segments.entries()) {
        const isLeaf = depth === segments.length - 1
        const missing = {targets: depth === 0 || !node ? [] : [node], missing: segment}
        if (isMap(node)) {
            const pair = node.items.find(item => isScalar(item.key) && String(item.key.value) === segment)
            if (!pair) return missing
            if (isLeaf && isPair(pair)) return {targets: pairTargets(pair)}
            node = pair.value as Node | null
        } else if (isSeq(node)) {
            const item = node.items[Number(segment)] as Node | undefined
            if (!item) return missing
            node = item
        } else {
            return missing
        }
    }
    return {targets: segments.length === 0 || !node ? [] : [node]}
}

function scalarRanges(targets: Node[]): Range[] {
    const ranges: Range[] = []
    for (const target of targets) {
        if (isScalar(target)) {
            if (target.range) ranges.push([target.range[0], target.range[1]])
            continue
        }
        visit(target, {
            Scalar(_, scalar) {
                if (scalar.range) ranges.push([scalar.range[0], scalar.range[1]])
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
    const doc = parseDocument(source, {lineCounter})
    if (doc.errors.length) return []

    return located.flatMap(error => {
        const {targets, missing} = locate(doc.contents, pointerSegments(error.pointer!))
        const message = missing && !/^\d+$/.test(missing) ? `${missing}: ${error.detail}` : error.detail!
        return lineSpans(source, scalarRanges(targets)).map(([start, end]) => {
            const from = lineCounter.linePos(start)
            const to = lineCounter.linePos(end)
            return {
                message,
                startLineNumber: from.line,
                startColumn: from.col,
                endLineNumber: to.line,
                endColumn: to.col,
            }
        })
    })
}
