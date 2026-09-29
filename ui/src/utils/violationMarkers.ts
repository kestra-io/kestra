import {isMap, isPair, isScalar, isSeq, LineCounter, parseDocument, type Node, type Pair} from "yaml"

export interface LocatedViolation {
    path: string;
    message: string;
}

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

function nodeRange(node: Node | null | undefined): Range | undefined {
    return node?.range ? [node.range[0], node.range[1]] : undefined
}

/** An existing key is marked from the key through a scalar value, or on the key alone when it holds a block. */
function pairRange(pair: Pair): Range | undefined {
    const key = nodeRange(pair.key as Node)
    if (!key) return undefined
    const value = isScalar(pair.value) ? nodeRange(pair.value) : undefined
    return [key[0], value?.[1] ?? key[1]]
}

/** A path that stops short of its leaf, a missing key, marks the deepest node that exists, except the document root. */
function locate(root: Node | null, segments: string[]): Range | undefined {
    let node: Node | null | undefined = root
    for (const [depth, segment] of segments.entries()) {
        const isLeaf = depth === segments.length - 1
        if (isMap(node)) {
            const pair = node.items.find(item => isScalar(item.key) && String(item.key.value) === segment)
            if (!pair) return depth === 0 ? undefined : nodeRange(node)
            if (isLeaf && isPair(pair)) return pairRange(pair)
            node = pair.value as Node | null
        } else if (isSeq(node)) {
            const item = node.items[Number(segment)] as Node | undefined
            if (!item) return depth === 0 ? undefined : nodeRange(node)
            node = item
        } else {
            return depth === 0 ? undefined : nodeRange(node)
        }
    }
    return segments.length === 0 ? undefined : nodeRange(node)
}

function trimTrailingWhitespace(source: string, [start, end]: Range): Range {
    let trimmed = end
    while (trimmed > start && /\s/.test(source[trimmed - 1])) trimmed--
    return [start, trimmed]
}

export function violationMarkers(source: string, violations: LocatedViolation[] | undefined): ViolationMarker[] {
    if (!violations?.length) return []
    const lineCounter = new LineCounter()
    const doc = parseDocument(source, {lineCounter})
    if (doc.errors.length) return []

    return violations.flatMap(violation => {
        const range = locate(doc.contents, pointerSegments(violation.path))
        if (!range) return []
        const [start, end] = trimTrailingWhitespace(source, range)
        const from = lineCounter.linePos(start)
        const to = lineCounter.linePos(end)
        return [{
            message: violation.message,
            startLineNumber: from.line,
            startColumn: from.col,
            endLineNumber: to.line,
            endColumn: to.col,
        }]
    })
}
