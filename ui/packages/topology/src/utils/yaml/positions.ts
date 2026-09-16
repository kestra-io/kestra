// What sits at a cursor position or on a given line.

import {
    LineCounter,
    YAMLMap,
    YAMLSeq,
    isMap,
    isNode,
    isSeq,
    parseDocument,
    visit,
    type Node,
} from "yaml"
import {parseDocumentTyped, scalarKey} from "./document.ts"
import {extractFieldFromMaps} from "./fields.ts"

function extractAllTypes(source: string, validTypes: string[] = []){
    return extractFieldFromMaps(source, "type", () => true, (value) =>
        validTypes.some((t) => t === value),
    )
}

export function getTypeAtPosition(
    source: string,
    position: { lineNumber: number; column: number },
    validTypes: string[],
) {
    const types = extractAllTypes(source, validTypes)

    const lineCounter = new LineCounter()
    parseDocument(source, {lineCounter})
    const cursorIndex =
        lineCounter.lineStarts[position.lineNumber - 1] + position.column

    for (const type of types.reverse()) {
        if (cursorIndex >= type.range[0]) {
            return type.type
        }
    }
    return null
}

export function getVersionAtPosition(
    source: string,
    position: { lineNumber: number; column: number },
) {
    const versions = extractAllVersions(source)
    const lineCounter = new LineCounter()
    parseDocument(source, {lineCounter})
    const cursorIndex =
        lineCounter.lineStarts[position.lineNumber - 1] + position.column

    for (const version of versions.reverse()) {
        if (cursorIndex >= version.range[0]) {
            return version.version
        }
    }
    return null
}

function extractAllVersions(source: string){
    return extractFieldFromMaps(source, "version", () => true, () => true, true)
}

const yamlKeyCapture = "([^:\\n]+): *"

const indentAndYamlKeyCapture = new RegExp(
    `(( *)(?:${yamlKeyCapture})?)[^\\n]*?$`,
)

function getParentKeyByChildIndent(
    stringToSearch: string,
    indent: number,
): { key: string; valueStartIndex: number } | undefined {
    if (indent < 2) {
        return undefined
    }

    const matches = stringToSearch.matchAll(
        new RegExp(`(?<! ) {${indent - 2}}(?! )${yamlKeyCapture}`, "g"),
    )
    const lastMatch = [...matches].pop()
    if (lastMatch === undefined) {
        return undefined
    }
    return {
        key: lastMatch[1],
        valueStartIndex: lastMatch.index + lastMatch[0].length,
    }
}

function extractIndentAndMaybeYamlKey(stringToTest: string): {
    indent: number;
    yamlKey: string | undefined;
    valueStartIndex: number | undefined;
} | undefined {
    const exec = indentAndYamlKeyCapture.exec(stringToTest)
    if (exec === null) {
        return undefined
    }

    const [stringBeforeValue, indent, yamlKey]: [
        string,
        string,
        string | undefined
    ] = [exec[1], exec[2], exec[3]]
    return {
        indent: indent.length,
        yamlKey,
        valueStartIndex:
            yamlKey === undefined
                ? undefined
                : exec.index + stringBeforeValue.length,
    }
}

export type YamlElement = {
    key?: string;
    value: Record<string, any>;
    parents: Record<string, any>[];
    range?: [number, number, number];
};

export function localizeElementAtIndex(source: string, indexInSource: number): YamlElement | undefined {
    const tillCursor = source.substring(0, indexInSource)

    const indentAndYamlKey = extractIndentAndMaybeYamlKey(tillCursor)
    let yamlKey = indentAndYamlKey?.yamlKey
    const indent = indentAndYamlKey?.indent ?? 0
    let valueStartIndex
    if (yamlKey === undefined) {
        const parentKeyExtract = getParentKeyByChildIndent(
            tillCursor,
            indent,
        )
        yamlKey = parentKeyExtract?.key
        valueStartIndex = parentKeyExtract?.valueStartIndex
    } else {
        valueStartIndex =
            tillCursor.lastIndexOf(yamlKey + ":") + yamlKey.length + 1
    }

    if (yamlKey === undefined || valueStartIndex === undefined) {
        return undefined
    }

    const yamlDoc = parseDocumentTyped(source)
    const elements: Required<YamlElement>[] = []

    visit(yamlDoc, {
        Pair(_, pair, parents) {
            const value = pair.value
            if (!isNode(value) || !value.range || scalarKey(pair) !== yamlKey) {
                return
            }
            const range = value.range
            const beforeElement = source.substring(0, range[0])
            elements.push({
                parents: parents
                    .filter((p) => isMap(p))
                    .map((p) => p.toJS(yamlDoc)),
                key: yamlKey,
                value: value.toJS(yamlDoc),
                range: [
                    range[0] -
                    (beforeElement.length -
                        beforeElement.replace(/\s*$/g, "").length),
                    range[1],
                    range[2],
                ],
            })
        },
    })

    const filter = elements.filter(
        (element) =>
            element.range[0] <= valueStartIndex && valueStartIndex <= element.range[2],
    )
    return filter.sort((a, b) => b.range[0] - a.range[0])?.[0]
}

function chartItemsOf(map: YAMLMap<unknown, unknown>): Node[] {
    const items: Node[] = []
    for (const item of map.items) {
        if (scalarKey(item) === "charts" && isSeq<Node>(item.value)) {
            items.push(...item.value.items)
        }
    }
    return items
}

export function getAllCharts(source: string) {
    const yamlDoc = parseDocumentTyped(source)
    const charts: string[] = []

    visit(yamlDoc, {
        Map(_, map) {
            for (const chartItem of chartItemsOf(map)) {
                charts.push(chartItem.toJSON())
            }
        },
    })

    return charts
}

export function getChartAtPosition(source: string, position: { lineNumber: number; column: number }) {
    const yamlDoc = parseDocumentTyped(source)
    const lineCounter = new LineCounter()
    parseDocument(source, {lineCounter})
    const cursorIndex =
        lineCounter.lineStarts[position.lineNumber - 1] + position.column

    let chart: Node | undefined
    visit(yamlDoc, {
        Map(_, map) {
            for (const chartItem of chartItemsOf(map)) {
                const range = chartItem.range
                if (range && range[0] <= cursorIndex && range[1] >= cursorIndex) {
                    chart = chartItem
                    return visit.BREAK
                }
            }
        },
    })

    return chart ? chart.toJSON() : null
}

export function getTasksLines(
    source: string,
):Record<string, {start: number, end: number}> {
    const paddedSource = source + "\n"
    const yamlDoc = parseDocumentTyped(paddedSource)
    const lineCounter = new LineCounter()
    parseDocument(paddedSource, {lineCounter})

    let tasksLines: Record<string, {start: number, end: number}> = {}
    visit(yamlDoc, {
        Map(_, map) {
            for (const item of map.items) {
                if (scalarKey(item) !== "tasks") { // visit only root tasks block for now
                    continue
                }
                if (isSeq(item.value)) {
                    for (const task of item.value.items) {
                        if (isMap(task)) {
                            tasksLines = {
                                ...tasksLines,
                                ...getTasksAndFlowableLines(lineCounter, task),
                            }
                        }
                    }
                }
                return visit.BREAK
            }
        },
    })
    return tasksLines
}

function getTasksAndFlowableLines(lineCounter: LineCounter, task: YAMLMap) {
    let tasksLines: Record<string, {start: number, end: number}> = {}
    const taskId = task.get("id") as string | undefined
    if(taskId){
        if(task.range) {
            tasksLines[taskId] = {
                start: lineCounter.linePos(task.range[0]).line,
                end: lineCounter.linePos(task.range[1]).line - 1,
            }
        }
        const childTasks = new YAMLSeq<YAMLMap>()
        const tasksChilds = task.get("tasks") as YAMLSeq<YAMLMap> | undefined
        if (isSeq<YAMLMap>(tasksChilds)){
            tasksChilds.items.forEach(x => childTasks.add(x))
        }
        const thenChilds = task.get("then") as YAMLSeq<YAMLMap> | undefined
        if (isSeq<YAMLMap>(thenChilds)){
            thenChilds.items.forEach(x => childTasks.add(x))
        }

        const elseChilds = task.get("else") as YAMLSeq<YAMLMap> | undefined
        if (isSeq<YAMLMap>(elseChilds)){
            elseChilds.items.forEach(x => childTasks.add(x))
        }

        childTasks.items.forEach(childTask => {
            if(isMap(childTask)){
                tasksLines = {...tasksLines, ...getTasksAndFlowableLines(lineCounter, childTask)}
            }
        })
    } else {
        if (task.get("task")) {
            const nestedDagTaskField = task.get("task") as YAMLMap
            if(isMap(nestedDagTaskField)) {
                tasksLines = {...tasksLines, ...getTasksAndFlowableLines(lineCounter, nestedDagTaskField)}
            }
        }
    }
    return tasksLines
}
