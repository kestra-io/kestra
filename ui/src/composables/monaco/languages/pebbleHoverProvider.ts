import * as monaco from "monaco-editor/editor/editor.api.js"
import {isMap, isNode, isPair, isScalar, type Pair, type YAMLMap} from "yaml"
import type {JSONProperty} from "../../../components/plugins/schema/utils/schemaUtils"
import {flowSymbolsAtPosition, type FlowSymbol, type OffsetRange} from "./pebbleDefinitionProvider"

export type OutputPropertiesLoader = (taskType: string) => Promise<Record<string, JSONProperty> | undefined>

const MAX_SNIPPET_LINES = 15

function nodeRange(node: YAMLMap | Pair): OffsetRange | undefined {
    if (isPair(node)) {
        const start = isNode(node.key) ? node.key.range?.[0] : undefined
        const end = (isNode(node.value) ? node.value : isNode(node.key) ? node.key : undefined)?.range?.[1]
        return start === undefined || end === undefined ? undefined : [start, end]
    }
    return node.range ? [node.range[0], node.range[1]] : undefined
}

function descriptionPair(node: YAMLMap | Pair): Pair | undefined {
    return isMap(node) ? node.items.find((item) => isScalar(item.key) && item.key.value === "description") : undefined
}

function symbolSnippet(source: string, symbol: FlowSymbol): string | undefined {
    const range = nodeRange(symbol.node)
    if (!range) {
        return undefined
    }
    const excluded = descriptionPair(symbol.node)
    const excludedRange = excluded ? nodeRange(excluded) : undefined
    let offset = source.lastIndexOf("\n", range[0] - 1) + 1
    const lines: string[] = []
    for (const line of source.slice(offset, range[1]).trimEnd().split("\n")) {
        const lineEnd = offset + line.length
        if (!excludedRange || lineEnd < excludedRange[0] || offset >= excludedRange[1]) {
            lines.push(line)
        }
        offset = lineEnd + 1
    }
    const indent = Math.min(...lines.filter((line) => line.trim()).map((line) => line.length - line.trimStart().length))
    const dedented = lines.map((line) => line.slice(indent))
    return dedented.length > MAX_SNIPPET_LINES ? [...dedented.slice(0, MAX_SNIPPET_LINES), "..."].join("\n") : dedented.join("\n")
}

function symbolDoc(symbol: FlowSymbol): string | undefined {
    const description = descriptionPair(symbol.node)?.value
    const parts = [symbol.comment, isScalar(description) && typeof description.value === "string" ? description.value.trim() : undefined]
    const doc = parts.filter((part): part is string => !!part).join("\n\n")
    return doc || undefined
}

function codeBlock(code: string, language = "yaml"): string {
    const longestRun = Math.max(0, ...(code.match(/`+/g) ?? []).map((run) => run.length))
    const fence = "`".repeat(Math.max(3, longestRun + 1))
    return `${fence}${language}\n${code}\n${fence}`
}

function propertyType(property: JSONProperty): string | undefined {
    if (property.type) {
        return Array.isArray(property.type) ? property.type.join(" | ") : property.type
    }
    return property.$ref?.split("/").pop()
}

function outputPropertyHover(name: string, property: JSONProperty): monaco.IMarkdownString[] {
    const type = propertyType(property)
    const doc = [...new Set([property.title, property.description].filter((part): part is string => !!part?.trim()))].join("\n\n")
    return [
        {value: codeBlock(type ? `${name}: ${type}` : name)},
        ...(doc ? [{value: doc, isTrusted: false, supportHtml: false}] : []),
    ]
}

function symbolHover(source: string, symbol: FlowSymbol): monaco.IMarkdownString[] {
    const snippet = symbolSnippet(source, symbol)
    const doc = symbolDoc(symbol)
    return [
        ...(snippet ? [{value: codeBlock(snippet)}] : []),
        ...(doc ? [{value: doc, isTrusted: false, supportHtml: false}] : []),
    ]
}

async function symbolsDeclaring(
    symbols: FlowSymbol[],
    name: string,
    loadOutputProperties: OutputPropertiesLoader,
): Promise<{symbol: FlowSymbol; property: JSONProperty}[]> {
    const types = [...new Set(symbols.flatMap((symbol) => symbol.taskType ?? []))]
    const schemas = new Map(await Promise.all(types.map(async (type) => [type, await loadOutputProperties(type).catch(() => undefined)] as const)))
    return symbols.flatMap((symbol) => {
        const property = symbol.taskType ? schemas.get(symbol.taskType)?.[name] : undefined
        return property ? [{symbol, property}] : []
    })
}

export async function providePebbleHover(
    model: monaco.editor.ITextModel,
    position: monaco.IPosition,
    loadOutputProperties: OutputPropertiesLoader,
    token?: monaco.CancellationToken,
): Promise<monaco.languages.Hover | null> {
    const resolved = flowSymbolsAtPosition(model, position)
    if (!resolved || resolved.symbols.length === 0) {
        return null
    }
    const {ref, symbols} = resolved
    const range: monaco.IRange = {
        startLineNumber: position.lineNumber,
        startColumn: ref.startColumn,
        endLineNumber: position.lineNumber,
        endColumn: ref.endColumn,
    }

    const source = model.getValue()
    const outputName = ref.root === "trigger" ? ref.segments[0] : ref.root === "outputs" && ref.segmentIndex >= 1 ? ref.segments[1] : undefined
    if (outputName !== undefined) {
        const declaring = await symbolsDeclaring(symbols, outputName, loadOutputProperties)
        if (token?.isCancellationRequested) {
            return null
        }
        if (declaring.length > 0) {
            const contents = outputPropertyHover(outputName, declaring[0].property)
            if (ref.root === "trigger") {
                const ids = declaring.map(({symbol}) => `$(zap) \`${source.slice(...symbol.id)}\``).join("  ")
                contents.push({value: ids, supportThemeIcons: true})
            }
            return {range, contents}
        }
    }

    const contents = symbols.flatMap((symbol) => symbolHover(source, symbol))
    return contents.length === 0 ? null : {range, contents}
}

export function registerPebbleHoverProvider(disposables: monaco.IDisposable[], loadOutputProperties: OutputPropertiesLoader): void {
    disposables.push(monaco.languages.registerHoverProvider("yaml", {
        provideHover: (model, position, token) => providePebbleHover(model, position, loadOutputProperties, token),
    }))
}
