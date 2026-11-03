import * as monaco from "monaco-editor/editor/editor.api.js"
import {isMap, isNode, isScalar, isSeq, parseDocument, Scalar, visit, type Document, type YAMLMap} from "yaml"

export interface PebbleReference {
    root: string
    segments: string[]
    /** Index into `segments` of the identifier under the cursor, -1 when the cursor is on the root word. */
    segmentIndex: number
    startColumn: number
    endColumn: number
}

type OffsetRange = [number, number]

const DELIMITER = /\{\{|\{%|\}\}|%\}/g
const CHAIN = /[A-Za-z_]\w*(?:\.\w+|\[\s*(?:'[^']*'|"[^"]*")\s*\])*/g
const SEGMENT = /\.(\w+)|\[\s*(?:'([^']*)'|"([^"]*)")\s*\]/g
const TASK_ROOTS = ["tasks", "errors", "finally", "afterExecution"]

function openPebbleBlock(lineText: string, cursor: number): {start: number; end: number} | undefined {
    let start: number | undefined
    for (const match of lineText.matchAll(DELIMITER)) {
        const opener = match[0].startsWith("{")
        if (match.index >= cursor) {
            if (start !== undefined && !opener) {
                return {start, end: match.index}
            }
            break
        }
        if (opener) {
            start = match.index + 2 <= cursor ? match.index + 2 : start
        } else {
            start = undefined
        }
    }
    return start === undefined ? undefined : {start, end: lineText.length}
}

export function pebbleReferenceAtPosition(lineText: string, column: number): PebbleReference | undefined {
    const cursor = column - 1
    const block = openPebbleBlock(lineText, cursor)
    if (!block) {
        return undefined
    }
    for (const chain of lineText.slice(block.start, block.end).matchAll(CHAIN)) {
        const chainStart = block.start + chain.index
        if (cursor < chainStart || cursor > chainStart + chain[0].length) {
            continue
        }
        const root = /^\w+/.exec(chain[0])?.[0] ?? ""
        const segments: string[] = []
        let hit: {segmentIndex: number; start: number; end: number} | undefined =
            cursor <= chainStart + root.length ? {segmentIndex: -1, start: chainStart, end: chainStart + root.length} : undefined
        for (const segment of chain[0].slice(root.length).matchAll(SEGMENT)) {
            const text = segment[1] ?? segment[2] ?? segment[3]
            const segmentStart = chainStart + root.length + segment.index
            const innerStart = segmentStart + segment[0].indexOf(text, 1)
            if (!hit && cursor >= segmentStart && cursor <= segmentStart + segment[0].length) {
                hit = {segmentIndex: segments.length, start: innerStart, end: innerStart + text.length}
            }
            segments.push(text)
        }
        return hit && {root, segments, segmentIndex: hit.segmentIndex, startColumn: hit.start + 1, endColumn: hit.end + 1}
    }
    return undefined
}

function scalarString(node: unknown): string | undefined {
    return isScalar(node) && typeof node.value === "string" ? node.value : undefined
}

function valueRange(node: unknown): OffsetRange | undefined {
    if (!isScalar(node) || !node.range) {
        return undefined
    }
    let [start, end] = [node.range[0], node.range[1]]
    if (node.type === Scalar.QUOTE_DOUBLE || node.type === Scalar.QUOTE_SINGLE) {
        start += 1
        end -= 1
    }
    return [start, end]
}

function rangesOf(range: OffsetRange | undefined): OffsetRange[] {
    return range ? [range] : []
}

function findById(seq: unknown, id: string): YAMLMap | undefined {
    return isSeq(seq)
        ? seq.items.find((item): item is YAMLMap => isMap(item) && scalarString(item.get("id", true)) === id)
        : undefined
}

function inputDefinition(root: YAMLMap, ref: PebbleReference): OffsetRange[] {
    const input = findById(root.get("inputs", true), ref.segments[0])
    if (!input) {
        return []
    }
    if (ref.segmentIndex === 0) {
        return rangesOf(valueRange(input.get("id", true)))
    }
    if (ref.segmentIndex === 1 && scalarString(input.get("type", true)) === "FORM") {
        const child = findById(input.get("inputs", true), ref.segments[1])
        return rangesOf(child && valueRange(child.get("id", true)))
    }
    return []
}

function taskDefinitions(root: YAMLMap, id: string): OffsetRange[] {
    const ranges: OffsetRange[] = []
    for (const key of TASK_ROOTS) {
        const node = root.get(key, true)
        if (!isNode(node)) {
            continue
        }
        visit(node, {
            Map(_key, map) {
                if (map.has("type") && scalarString(map.get("id", true)) === id) {
                    ranges.push(...rangesOf(valueRange(map.get("id", true))))
                }
            },
        })
    }
    return ranges
}

function keyDefinition(map: unknown, key: string): OffsetRange[] {
    if (!isMap(map)) {
        return []
    }
    return rangesOf(valueRange(map.items.find((item) => scalarString(item.key) === key)?.key))
}

function labelDefinition(labels: unknown, key: string): OffsetRange[] {
    if (isSeq(labels)) {
        const label = labels.items.find((item) => isMap(item) && scalarString(item.get("key", true)) === key)
        return rangesOf(isMap(label) ? valueRange(label.get("key", true)) : undefined)
    }
    return keyDefinition(labels, key)
}

export function resolveFlowDefinition(source: string, ref: PebbleReference): OffsetRange[] {
    if (ref.segmentIndex < 0) {
        return []
    }
    let document: Document
    try {
        document = parseDocument(source)
    } catch {
        return []
    }
    const root = document.contents
    if (!isMap(root)) {
        return []
    }
    switch (ref.root) {
    case "inputs":
        return inputDefinition(root, ref)
    case "outputs":
        return taskDefinitions(root, ref.segments[0])
    case "vars":
        return ref.segmentIndex === 0 ? keyDefinition(root.get("variables", true), ref.segments[0]) : []
    case "labels":
        return ref.segmentIndex === 0 ? labelDefinition(root.get("labels", true), ref.segments[0]) : []
    default:
        return []
    }
}

let lastResolution: {key: string; ranges: OffsetRange[]} | undefined

export function providePebbleDefinition(model: monaco.editor.ITextModel, position: monaco.IPosition): monaco.languages.LocationLink[] | null {
    if (!model.uri.path.includes("flow-")) {
        return null
    }
    const ref = pebbleReferenceAtPosition(model.getLineContent(position.lineNumber), position.column)
    if (!ref) {
        return null
    }
    const key = JSON.stringify([model.uri.toString(), model.getVersionId(), ref.root, ref.segmentIndex, ref.segments])
    const ranges = lastResolution?.key === key ? lastResolution.ranges : resolveFlowDefinition(model.getValue(), ref)
    lastResolution = {key, ranges}
    if (ranges.length === 0) {
        return null
    }
    const originSelectionRange: monaco.IRange = {
        startLineNumber: position.lineNumber,
        startColumn: ref.startColumn,
        endLineNumber: position.lineNumber,
        endColumn: ref.endColumn,
    }
    return ranges.map(([start, end]) => {
        const from = model.getPositionAt(start)
        const to = model.getPositionAt(end)
        const range: monaco.IRange = {
            startLineNumber: from.lineNumber,
            startColumn: from.column,
            endLineNumber: to.lineNumber,
            endColumn: to.column,
        }
        return {uri: model.uri, range, targetSelectionRange: range, originSelectionRange}
    })
}

export function registerPebbleDefinitionProvider(disposables: monaco.IDisposable[]): void {
    disposables.push(monaco.languages.registerDefinitionProvider("yaml", {provideDefinition: providePebbleDefinition}))
}
