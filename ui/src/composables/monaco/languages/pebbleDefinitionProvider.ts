import * as monaco from "monaco-editor/editor/editor.api.js"
import {isMap, isNode, isPair, isScalar, isSeq, parseDocument, Scalar, visit, type Document, type Pair, type YAMLMap} from "yaml"

export interface PebbleReference {
    root: string
    segments: string[]
    /** Index into `segments` of the identifier under the cursor, -1 when the cursor is on the root word. */
    segmentIndex: number
    startColumn: number
    endColumn: number
}

export type OffsetRange = [number, number]

export interface FlowSymbol {
    id: OffsetRange
    node: YAMLMap | Pair
    comment?: string
    taskType?: string
}

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

// yaml attaches the comment above a collection's first item to the collection itself.
function leadingComment(parent: unknown, item: YAMLMap | Pair): string | undefined {
    const own = isPair(item) ? (isNode(item.key) ? item.key.commentBefore : undefined) : item.commentBefore
    const inherited = (isSeq(parent) || isMap(parent)) && parent.items[0] === item ? parent.commentBefore : undefined
    return docComment(own ?? inherited)
}

// A blank line ends a comment group, so only the group touching the key documents it.
function docComment(raw: string | null | undefined): string | undefined {
    const lines = raw?.split("\n") ?? []
    const group = lines.slice(lines.lastIndexOf("") + 1)
    return group.map((line) => line.replace(/^ /, "")).join("\n").trim() || undefined
}

function symbolsOf(node: YAMLMap | Pair, idNode: unknown, parent: unknown): FlowSymbol[] {
    const id = valueRange(idNode)
    return id ? [{id, node, comment: leadingComment(parent, node)}] : []
}

function findById(seq: unknown, id: string): YAMLMap | undefined {
    return isSeq(seq)
        ? seq.items.find((item): item is YAMLMap => isMap(item) && scalarString(item.get("id", true)) === id)
        : undefined
}

function inputSymbols(root: YAMLMap, ref: PebbleReference): FlowSymbol[] {
    const inputs = root.get("inputs", true)
    const input = findById(inputs, ref.segments[0])
    if (!input) {
        return []
    }
    if (ref.segmentIndex === 0) {
        return symbolsOf(input, input.get("id", true), inputs)
    }
    if (ref.segmentIndex === 1 && scalarString(input.get("type", true)) === "FORM") {
        const children = input.get("inputs", true)
        const child = findById(children, ref.segments[1])
        return child ? symbolsOf(child, child.get("id", true), children) : []
    }
    return []
}

function taskSymbols(root: YAMLMap, id: string): FlowSymbol[] {
    const symbols: FlowSymbol[] = []
    for (const key of TASK_ROOTS) {
        const node = root.get(key, true)
        if (!isNode(node)) {
            continue
        }
        visit(node, {
            Map(_key, map, path) {
                if (map.has("type") && scalarString(map.get("id", true)) === id) {
                    const taskType = scalarString(map.get("type", true))
                    symbols.push(...symbolsOf(map, map.get("id", true), path.at(-1)).map((symbol) => ({...symbol, taskType})))
                }
            },
        })
    }
    return symbols
}

function keySymbols(map: unknown, key: string): FlowSymbol[] {
    if (!isMap(map)) {
        return []
    }
    const pair = map.items.find((item) => scalarString(item.key) === key)
    return pair ? symbolsOf(pair, pair.key, map) : []
}

function triggerSymbols(triggers: unknown): FlowSymbol[] {
    if (!isSeq(triggers)) {
        return []
    }
    return triggers.items.flatMap((trigger) => isMap(trigger)
        ? symbolsOf(trigger, trigger.get("id", true), triggers).map((symbol) => ({...symbol, taskType: scalarString(trigger.get("type", true))}))
        : [])
}

function labelSymbols(labels: unknown, key: string): FlowSymbol[] {
    if (isSeq(labels)) {
        const label = labels.items.find((item): item is YAMLMap => isMap(item) && scalarString(item.get("key", true)) === key)
        return label ? symbolsOf(label, label.get("key", true), labels) : []
    }
    return keySymbols(labels, key)
}

export function resolveFlowSymbols(source: string, ref: PebbleReference): FlowSymbol[] {
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
        return inputSymbols(root, ref)
    case "outputs":
        return taskSymbols(root, ref.segments[0])
    case "vars":
        return ref.segmentIndex === 0 ? keySymbols(root.get("variables", true), ref.segments[0]) : []
    case "labels":
        return ref.segmentIndex === 0 ? labelSymbols(root.get("labels", true), ref.segments[0]) : []
    case "trigger":
        return triggerSymbols(root.get("triggers", true))
    default:
        return []
    }
}

export function resolveFlowDefinition(source: string, ref: PebbleReference): OffsetRange[] {
    return resolveFlowSymbols(source, ref).map((symbol) => symbol.id)
}

let lastResolution: {key: string; symbols: FlowSymbol[]} | undefined

export function flowSymbolsAtPosition(model: monaco.editor.ITextModel, position: monaco.IPosition): {ref: PebbleReference; symbols: FlowSymbol[]} | undefined {
    if (!model.uri.path.includes("flow-")) {
        return undefined
    }
    const ref = pebbleReferenceAtPosition(model.getLineContent(position.lineNumber), position.column)
    if (!ref) {
        return undefined
    }
    const key = JSON.stringify([model.uri.toString(), model.getVersionId(), ref.root, ref.segmentIndex, ref.segments])
    const symbols = lastResolution?.key === key ? lastResolution.symbols : resolveFlowSymbols(model.getValue(), ref)
    lastResolution = {key, symbols}
    return {ref, symbols}
}

export function providePebbleDefinition(model: monaco.editor.ITextModel, position: monaco.IPosition): monaco.languages.LocationLink[] | null {
    const resolved = flowSymbolsAtPosition(model, position)
    if (!resolved || resolved.symbols.length === 0) {
        return null
    }
    const {ref} = resolved
    const originSelectionRange: monaco.IRange = {
        startLineNumber: position.lineNumber,
        startColumn: ref.startColumn,
        endLineNumber: position.lineNumber,
        endColumn: ref.endColumn,
    }
    return resolved.symbols.map(({id: [start, end]}) => {
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
