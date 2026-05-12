import {dump, load} from "js-yaml"
import {
    Scalar,
    YAMLMap,
    YAMLSeq,
    Document,
   type Node,
    Pair,
    LineCounter,
    parseDocument,
    isPair,
    isMap,
    isSeq,
    visit,
    type Range,
    type ToStringOptions,
} from "yaml"
import {cloneDeep} from "./util"

export function parse<T = any>(item?: string, throwIfError = true): T | undefined {
    if (item === undefined) return undefined

    try {
        return load(item) as any
    } catch (e) {
        if (throwIfError) throw e
        return undefined
    }
}

const CRON_LINE_REGEX = /^(\s*-?\s*cron:\s*)([^\n#]*?)(\s*(#.*)?)$/gm

function preserveCronQuotes(yamlContent: string) {
    return yamlContent.replace(
        CRON_LINE_REGEX,
        (fullLine, prefix: string, valuePart: string, suffix: string) => {
            const value = valuePart.trim()
            const isEmptyValue = value === "" || value === "\"\"" || value === "''"

            if (isEmptyValue) {
                // Leave empty cron values untouched and without extra quotes.
                return `${prefix}${suffix ?? ""}`
            }

            const shouldSkip =
                value.startsWith("\"") ||
                value.startsWith("'") ||
                value.startsWith("|") ||
                value.startsWith(">")

            if (shouldSkip) {
                return fullLine
            }

            return `${prefix}"${value}"${suffix ?? ""}`
        },
    )
}

export function stringify(item: any) {
    if (item === undefined) return ""

    const clonedValue = cloneDeep(item)
    delete clonedValue.deleted

    const yamlContent = dump(transform(clonedValue), {
        lineWidth: -1,
        noCompatMode: true,
        quotingType: "\"",
    })

    return preserveCronQuotes(yamlContent)
}

const SORT_FIELDS = [
    "id",
    "type",
    "namespace",
    "description",
    "revision",
    "inputs",
    "variables",
    "tasks",
    "errors",
    "triggers",
    "listeners",
    "pluginDefaults",
]

function sortPredicate(a: string, b: string) {
    const aIndex = SORT_FIELDS.indexOf(a)
    const bIndex = SORT_FIELDS.indexOf(b)
    const aIndexProtected = aIndex >= 0 ? aIndex : Number.MAX_SAFE_INTEGER
    const bIndexProtected = bIndex >= 0 ? bIndex : Number.MAX_SAFE_INTEGER

    return aIndexProtected - bIndexProtected
}

function sort(value: Record<string, any>) {
    return Object.keys(value)
        .sort(sortPredicate)
}

export function pairsToMap(pairs?: any[]) {
    const map = new YAMLMap()
    if (!isPair(pairs?.[0])) {
        return map
    }

    for (const pair of pairs!) {
        map.add(pair)
    };
    return map
}

function transform(value: any): any {
    if (value instanceof Array) {
        return value.map((r) => {
            return transform(r)
        })
    } else if (typeof value === "string" || value instanceof String) {
        return value
    } else if (value instanceof Object) {
        return sort(value).reduce((accumulator, r) => {
            if (value[r] !== undefined) {
                accumulator[r] = transform(value[r])
            }

            return accumulator
        }, Object.create({}))
    }

    return value
}

function getSectionNodeAndDocumentFromSource({source, section}: {
    source: string,
    section: string
}) {
    const yamlDoc = parseDocumentTyped(source)
    const sectionNode = getSectionFromDocument({yamlDoc, section})
    return {yamlDoc, sectionNode}
}

function parseDocumentTyped(source: string) {
    return parseDocument(source) as Document<YAMLMap<Scalar<string>, Node>>
}

function getSectionFromDocument({yamlDoc, section}:
    {
        yamlDoc: Document<YAMLMap<{ value: string }, Node>>,
        section: string
    }) {
    const sectionNode = yamlDoc.contents?.items?.find(
        (e) => e.key.value === section,
    ) as { value: YAMLSeq<YAMLMap<{ value: string }, Node>> } | undefined
    return sectionNode?.value
}

function getPathFromId({node, id} : {
    node: Node,
    id: string
}): (string | number)[] | undefined {
    // recursively search for the id in the node
    if (isSeq<{ value: Node }>(node)) {
        let index = 0
        for (const item of node.items) {
            if (isMap<{ value: string }, Node>(item)) {
                const itemId = item.get("id") as string | undefined
                if (itemId === id) {
                    return [index]
                } else {
                    const path = getPathFromId({node: item, id})
                    if (path) {
                        return [index, ...path]
                    }
                }
            }
            index++
        }
    }

    if( isMap<{ value: string }, Node>(node)) {
        const itemId = node.get("id") as string | undefined
        if (itemId === id) {
            return []
        } else {
            for (const item of node.items) {
                if(item.value) {
                    const path = getPathFromId({node: item.value, id})
                    if (path) {
                        return [item.key.value, ...path]
                    }
                }
            }
        }
    }

    return undefined
}

export function getPathFromSectionAndId({
    source,
    section,
    id,
}: {
    source: string,
    section: string,
    id: string
}): string | undefined {
    const {sectionNode} = getSectionNodeAndDocumentFromSource({source, section})
    if (!sectionNode) {
        return undefined
    }

    const pathArray = getPathFromId({node: sectionNode, id}) ?? []
    const path = pathArray.map((e) => {
        if (typeof e === "string") {
            return `.${e}`
        } else {
            return `[${e}]`
        }
    }).join("")

    return `${section}${path}`
}



export function extractBlock({source, section, key, keyName}: {
    source: string,
    section: string,
    key: string,
    keyName?: string
}) {
    if (!keyName) {
        keyName = "id"
    }
    const {sectionNode} = getSectionNodeAndDocumentFromSource({source, section})
    if (!sectionNode) {
        return undefined
    }

    const blockNode = extractBlockFromDocument({
        yamlDoc: sectionNode,
        keyName,
        key,
    })

    return blockNode === undefined
        ? undefined
        : new Document(blockNode).toString(TOSTRING_OPTIONS)
}

function extractBlockFromDocument({yamlDoc, keyName, key, callback}: {
    yamlDoc: Node,
    keyName: string,
    key: string,
    /**
     * Callback function to modify the found element
     * @param element The found YAMLMap element
     */
    callback?: (element: YAMLMap<{ value: string }, string | Node>) => any,
}) {
    function find(element?: Node): Node | void {
        if (!element) {
            return
        }
        if (isMap<{ value: string }, string | Node>(element)) {
            if (element.get("type") !== undefined && key === element.get(keyName)) {
                return callback ? callback(element) : element
            }
        }
        if (isSeq<{ value: Node }>(element)
            || isMap<{ value: string }, Node>(element)) {
            for (const [itemIndex, item] of element.items.entries()) {
                const result = isMap(item)
                    ? find(item)
                    : find(item.value ?? undefined)

                if (result) {
                    if (callback) {
                        if (isMap(element) && isPair<{ value: string }, Node>(item)) {
                            // replace value in the map
                            element.set(item.key, result)
                        } else {
                            element.items[itemIndex] = result as any
                        }
                    } else if (result) {
                        return result
                    }
                }
            }
        }
    }

    const result = find(yamlDoc)

    if (result === undefined) {
        return undefined
    }

    if (callback) {
        return new Document(result)
    } else {
        return new Document(result)
    }
}

export function extractBlockWithPath({source, path}: {
    source: string,
    path: string
}) {
    const doc = extractBlockWithPathFromDocument({
        yamlDoc: parseDocumentTyped(source),
        path,
    })
    if (!doc) {
        return undefined
    }
    return new Document(doc).toString(TOSTRING_OPTIONS)
}

function extractBlockWithPathFromDocument({yamlDoc, path, callback}: {
    yamlDoc: Document<YAMLMap<{ value: string }, Node>>,
    path: string,
    callback?: (element: YAMLMap<{ value: string }, Node>) => any
}) {
    const parsedPath = parsePath(path)
    const element = yamlDoc.getIn(parsedPath) as YAMLMap<{ value: string }, Node>
    if (element === undefined) {
        return undefined
    }
    if (callback) {
        const replacedEl = callback(element)
        yamlDoc.setIn(parsedPath, replacedEl)
        return new Document(replacedEl)
    }
    return new Document(element)
}

export function replaceBlockWithPath({source, path, newContent}: {
    source: string,
    path: string,
    newContent: string
}) {
    const yamlDoc = parseDocumentTyped(source)
    const pathArray = parsePath(path)
    // if value is empty, delete the property instead of replacing it with an empty value
    if(newContent === ""){
        yamlDoc.deleteIn(pathArray)
        return yamlDoc.toString(TOSTRING_OPTIONS)
    }
    const newItem = yamlDoc.createNode(parseDocument(newContent))
    const insertBlock = !yamlDoc.hasIn(pathArray)
    yamlDoc.setIn(pathArray, newItem)

    // When inserting a top level element
    // try to insert the key at the right place
    if (insertBlock && pathArray.length === 1
        && yamlDoc.contents && isMap(yamlDoc.contents)) {
        yamlDoc.contents.items.sort((a, b) => sortPredicate(a.key.value ?? a.key, b.key.value ?? a.key))
    }

    return yamlDoc.toString(TOSTRING_OPTIONS)
}

export function swapBlocks({source, section, key1, key2, keyName}: {
    source: string,
    section: string,
    key1: string,
    key2: string,
    keyName?: string
}) {
    if (!keyName) {
        keyName = "id"
    }
    const {yamlDoc, sectionNode} = getSectionNodeAndDocumentFromSource({source, section})
    if (!sectionNode) {
        return source
    }
    const task1 = extractBlockFromDocument({yamlDoc: sectionNode, keyName, key: key1})
    const task2 = extractBlockFromDocument({yamlDoc: sectionNode, keyName, key: key2})

    if (!task1 || !task2) {
        return source
    }

    visit(yamlDoc, {
        Pair(_, pair: any) {
            if (
                pair.key.value === "dependsOn" &&
                pair.value.items.map((e: any) => e.value).includes(key1)
            ) {
                throw {
                    message: "dependency task",
                    messageOptions: {taskId: key2},
                }
            }
        },
    })

    extractBlockFromDocument({yamlDoc: sectionNode, keyName, key: key1, callback: () => task2})
    extractBlockFromDocument({yamlDoc: sectionNode, keyName, key: key2, callback: () => task1})

    return yamlDoc.toString(TOSTRING_OPTIONS)
}


function getNodeIndexInParent(
    yamlDoc: Document<YAMLMap<{ value: string }, Node>>,
    patentNode: YAMLMap<{ value: string }, Node>,
    parentPath: (string|number)[],
    refPath?: string | number,
    position: "before" | "after" = "after",
) {
    if (refPath === undefined) {
        return position === "before" ? 0 : patentNode.items.length - 1
    }

    const indexNode = yamlDoc.getIn([...parentPath, refPath]) as any

    return patentNode.items.indexOf(indexNode)
}

export function parsePath(path: string) {
    const pathParts = path.split(".")
    return pathParts.reduce((acc: (string|number)[], part) => {
        // if the path has a number, it will look like this
        // path[0]

        const numberPath = part.match(/\[(\d+)\]$/)
        if (numberPath?.[0]) {
            acc.push(part.slice(0, numberPath.index))
            acc.push(parseInt(numberPath[1], 10))
        } else {
            acc.push(part)
        }
        return acc
    }, [])
}

function getParentNode(yamlDoc: ReturnType<typeof parseDocumentTyped>, parentPath: string) {
    if(!parentPath.includes(".")){
        if(!yamlDoc.contents){
            throw new Error(`Document is empty, cannot insert block with path ${parentPath}`)
        }
        return yamlDoc.contents
    } else {
        // remove everything after the last "."
        const parentPathWithoutKey = parentPath.substring(0, parentPath.lastIndexOf("."))
        const parentNode = yamlDoc.getIn(parsePath(parentPathWithoutKey)) as YAMLMap<{ value: string }, Node>
        if (!parentNode) {
            // create the missing parent node
            const newParentNode = createParentNode(parentPathWithoutKey)
            // attach it to the parents parent
            const parentParentNode = getParentNode(yamlDoc, parentPathWithoutKey)
            parentParentNode?.items.push(newParentNode)
            return newParentNode.value
        }
        return parentNode
    }
}

function createParentNode(parentPath: string) {
    const newParentNode = new YAMLSeq()
    const parentKey = parentPath.split(".").pop() as string
    const parentKeyNode = new Pair(new Scalar(parentKey), newParentNode)
    return parentKeyNode
}

function createPairNode(parentKey: string, newPropNode: Node) {
    const newPairNodeValue = new YAMLSeq()
    newPairNodeValue.add(newPropNode)
    return new Pair(new Scalar(parentKey), newPairNodeValue)
}

export function insertBlockWithPath({
    source,
    newBlock,
    refPath,
    position,
    parentPath,
}: {
    source: string,
    parentPath: string,
    newBlock: string,
    refPath?: string | number,
    position?: "before" | "after",
}){
    if (!position) {
        position = "after"
    }
    const yamlDoc = parseDocumentTyped(source)
    const newPropNode = yamlDoc.createNode(parseDocument(newBlock)) as any

    const parsedPath = parsePath(parentPath)

    const parentNode = yamlDoc.getIn(parsedPath) as YAMLMap<{ value: string }, Node>

    if (!parentNode) {
        const newPairNode = createPairNode(parentPath.split(".").pop() as string, newPropNode)
        const newParentNode = getParentNode(yamlDoc, parentPath)
        newParentNode?.items.push(newPairNode)
        return yamlDoc.toString(TOSTRING_OPTIONS)
    }

    const index = getNodeIndexInParent(yamlDoc, parentNode, parsedPath, refPath)
    if (position === "before") {
        parentNode.items.splice(index, 0, newPropNode)
    } else {
        parentNode.items.splice(index + 1, 0, newPropNode)
    }

    return yamlDoc.toString(TOSTRING_OPTIONS)
}


export function deleteBlock({source, section, key, keyName}: {
    source: string,
    section: string,
    key: string,
    keyName?: string
}) {
    if (!keyName) {
        keyName = "id"
    }
    const yamlDoc = parseDocumentTyped(source)
    visit(yamlDoc, {
        Pair(_, pair: any) {
            if (pair.key.value === section) {
                visit(pair.value, {
                    Map(__, map) {
                        if (map.get(keyName) === key) {
                            return visit.REMOVE
                        }
                    },
                })
            }
        },
    })

    // delete empty sections
    visit(yamlDoc, {
        Pair(_, pair) {
            if (isSeq(pair.value) && pair.value.items.length === 0) {
                return visit.REMOVE
            }
        },
    })
    return yamlDoc.toString(TOSTRING_OPTIONS)
}


export function replaceIdAndNamespace(source: string, id: string, namespace: string) {
    const yamlDoc = parseDocumentTyped(source)
    yamlDoc.contents = yamlDoc.contents || new YAMLMap()

    const existingNamespace = yamlDoc.getIn(["namespace"], true) as Scalar | undefined
    if(existingNamespace){
        existingNamespace.value = namespace
    }else{
        yamlDoc.contents.items.unshift(new Pair(new Scalar("namespace"), new Scalar(namespace)))
    }

    const existingId = yamlDoc.getIn(["id"], true) as Scalar | undefined
    if(existingId){
        existingId.value = id
    }else{
        yamlDoc.contents.items.unshift(new Pair(new Scalar("id"), new Scalar(id)))
    }

    return yamlDoc.toString(TOSTRING_OPTIONS)
}

export function updateMetadata(source: string, metadata: Record<string, any>) {
    // TODO: check how to keep comments
    const yamlDoc = parseDocument(source) as any

    if (!yamlDoc?.contents?.items) {
        return source
    }

    for (const property in metadata) {
        if (
            yamlDoc.contents.items.find((item: any) => item.key.value === property)
        ) {
            yamlDoc.contents.items.find(
                (item: any) => item.key.value === property,
            ).value = metadata[property]
        } else {
            yamlDoc.contents.items.push(
                new Pair(new Scalar(property), metadata[property]),
            )
        }
    }
    return cleanMetadataDocument(yamlDoc).toString(TOSTRING_OPTIONS)
}

const FLOW_SECTION_KEYS = [
    "tasks",
    "triggers",
    "errors",
    "finally",
    "afterExecution",
    "pluginDefaults",
] as const

const ORDERED_FLOW_ROOT_KEYS = [
    "id",
    "type",
    "namespace",
    "description",
    "retry",
    "labels",
    "inputs",
    "variables",
    ...FLOW_SECTION_KEYS,
    "taskDefaults",
    "concurrency",
    "sla",
    "outputs",
    "disabled",
] as const

function isItemTruthy(item: Node) {
    if (isSeq(item) || isMap(item)) {
        return item.items.length > 0
    } else {
        return true
    }
}

function cleanMetadataDocument(yamlDoc: Document<YAMLMap<Scalar<string>, Node | YAMLSeq>>) {
    if (!yamlDoc?.contents?.items) {
        return yamlDoc
    }
    const updatedItems: Pair<Scalar<string>, Node>[] = []
    for (const prop of ORDERED_FLOW_ROOT_KEYS) {
        const item = yamlDoc.contents?.items.find(
            (e: any) => (e.key.value ?? e.key) === prop,
        )
        if (item?.value && isItemTruthy(item.value)) {
            updatedItems.push(item)
            if(isSeq(item.value)) {
                if (!item.key.commentBefore) {
                    item.key.spaceBefore = true
                }
                // add whitespace between items
                item.value.items.forEach((seqItem: any, index: number) => {
                    if(index === 0) {
                        return
                    }
                    if (seqItem.commentBefore) {
                        seqItem.commentBefore.spaceBefore = true
                    } else {
                        seqItem.spaceBefore = true
                    }
                })

            }
        }
    }
    yamlDoc.contents.items = updatedItems
    return yamlDoc
}

export function getMetadata(source: string): Record<string, any> {
    const yamlDoc = parseDocument(source) as any
    if(!yamlDoc.contents?.items) return {}
    const metadata: Record<string, any> = {}

    for (const item of yamlDoc.contents.items) {
        if (!FLOW_SECTION_KEYS.includes(item.key.value)) {
            metadata[item.key.value] =
                isMap(item.value) || isSeq(item.value)
                    ? item.value.toJSON()
                    : item.value.value
        }
    }
    return metadata
}

export function deleteMetadata(source: any, metadata: any) {
    const yamlDoc = parseDocument(source) as any

    if (!yamlDoc.contents.items) {
        return source
    }

    const item = yamlDoc.contents.items.find((e: any) => e.key.value === metadata)
    if (item) {
        yamlDoc.contents.items.splice(yamlDoc.contents.items.indexOf(item), 1)
    }

    return yamlDoc.toString(TOSTRING_OPTIONS)
}

export function flowHaveTasks(source: string) {
    const {sectionNode} = getSectionNodeAndDocumentFromSource({source, section: "tasks"})
    if (!sectionNode) {
        return false
    }
    return isSeq(sectionNode) && sectionNode.items.length > 0
}

/**
 * Specify a source yaml doc, the field to extract recursively in every map of the doc and optionally
 * a predicate to define which paths should be taken into account `parentPathPredicate`
 * will take a single argument which is the path of each parent property starting from the root doc (joined with ".")
 * "my.parent.task" will mean that the field was retrieved in my -> parent -> task path.
 */
export function extractFieldFromMaps<T extends string>(
    source: string,
    fieldName: T,
    parentPathPredicate = (_: any, __?: any) => true,
    valuePredicate = (_: any) => true,
    keepEmptyFields: boolean = false,
): (Record<T, any> & {range: Range})[] {
    const yamlDoc = parseDocument(source) as any
    const maps: any[] = []
    visit(yamlDoc, {
        Map(_, map, parent) {
            if (
                parentPathPredicate(
                    parent
                        .filter((p) => isPair(p))
                        .map((p: any) => p?.key?.value)
                        .join("."),
                ) &&
                map.items
            ) {
                let matched = false
                for (const item of map.items as any[]) {
                    if (item?.key?.value === fieldName) {
                        const fieldValue = item?.value?.value ?? item.value?.items
                        if (valuePredicate(fieldValue)) {
                            maps.push({
                                [fieldName]: fieldValue,
                                range: map.range,
                            })
                            matched = true
                        }
                    }
                }
                if (!matched && keepEmptyFields) {
                    // add an empty entry if the field cannot be matched (i.e., optional property).
                    maps.push({
                        [fieldName]: undefined,
                        range: map.range,
                    })
                }
            }
        },
    })
    return maps
}

function extractAllTypes(source: string, validTypes: string[] = []){
    return extractFieldFromMaps(source, "type", () => true, (value) =>
        validTypes.some((t) => t === value),
    )
}


/**
 * Get task type at cursor position.
 * Useful to display/update the live docs
 */
export function getTypeAtPosition(
    source: string,
    position: { lineNumber: number; column: number },
    validTypes: any,
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

/**
 * Get task version at cursor position.
 * Useful to display/update the live docs
 */
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

const TOSTRING_OPTIONS: ToStringOptions = {
    lineWidth: 0,
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

export function localizeElementAtIndex(source: string, indexInSource: number): YamlElement {
    const tillCursor = source.substring(0, indexInSource)

    const indentAndYamlKey: any = extractIndentAndMaybeYamlKey(tillCursor)
    let {yamlKey} = indentAndYamlKey
    const {indent} = indentAndYamlKey
    // We search in previous keys to find the parent key
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

    const yamlDoc = parseDocument(source) as any
    const elements: any = []

    visit(yamlDoc, {
        Pair(_: any, pair: any, parents: readonly any[]) {
            if (pair.value?.range !== undefined && pair.key.value === yamlKey) {
                const beforeElement = source.substring(0, pair.value.range[0])
                elements.push({
                    parents: parents
                        .filter((p) => isMap(p))
                        .map((p) => p.toJS(yamlDoc)),
                    key: pair.key.value,
                    value: pair.value.toJS(yamlDoc),
                    range: [
                        pair.value.range[0] -
                        (beforeElement.length -
                            beforeElement.replace(/\s*$/g, "").length),
                        ...pair.value.range.slice(1),
                    ],
                })
            }
        },
    })

    const filter = elements.filter(
        (map: any) =>
            map.range[0] <= valueStartIndex && valueStartIndex <= map.range[2],
    )
    return filter.sort((a: any, b: any) => b.range[0] - a.range[0])?.[0]
}

// CHARTS for dashboard

export function getAllCharts(source: string) {
    const yamlDoc = parseDocument(source) as any
    const charts: string[] = []

    visit(yamlDoc, {
        Map(_, map) {
            if (map.items) {
                for (const item of map.items as any[]) {
                    if (item?.key?.value === "charts" && item?.value?.items) {
                        for (const chartItem of item.value.items) {
                            charts.push(chartItem.toJSON())
                        }
                    }
                }
            }
        },
    })

    return charts
}

export function getChartAtPosition(source: string, position: { lineNumber: number; column: number }) {
    const yamlDoc = parseDocument(source) as any
    const lineCounter = new LineCounter()
    parseDocument(source, {lineCounter})
    const cursorIndex =
        lineCounter.lineStarts[position.lineNumber - 1] + position.column

    let chart: any
    visit(yamlDoc, {
        Map(_, map) {
            if (map.items) {
                for (const item of map.items as any[]) {
                    if (item?.key?.value === "charts") {
                        if (item?.value?.items) {
                            for (const chartItem of item.value.items) {
                                if (
                                    chartItem.range[0] <= cursorIndex &&
                                    chartItem.range[1] >= cursorIndex
                                ) {
                                    chart = chartItem
                                    return visit.BREAK
                                }
                            }
                        }
                    }
                }
            }
        },
    })

    return chart ? chart.toJSON() : null
}

/**
 * Get line start and end for all tasks
 * @param source - YAML source code
 * @returns lines infos for each taskId
 */
export function getTasksLines(
    source: string,
):Record<string, {start: number, end: number}> {
    // if a task ends on the last line of the YAML,
    // its end range will be one line off.
    // To avoid this specific case, we add a newline at the end of the source.
    const paddedSource = source + "\n"
    const yamlDoc = parseDocument(paddedSource) as any
    const lineCounter = new LineCounter()
    parseDocument(paddedSource, {lineCounter})

    let tasksLines: Record<string, {start: number, end: number}> = {}
    visit(yamlDoc, {
        Map(_, map) {
            if (map.items) {
                for (const item of map.items as any[]) {
                    if (item?.key?.value === "tasks") { // visit only root tasks block for now
                        if (item?.value?.items) {
                            for (const task of item.value.items) {
                                if(isMap(task)){
                                    const foundChilTasksLines = getTasksAndFlowableLines(lineCounter, task)
                                    tasksLines = {
                                        ...tasksLines,
                                        ...foundChilTasksLines,
                                    }
                                }
                            }
                        }
                        return visit.BREAK
                    }
                }
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
        // io.kestra.plugin.core.flow special case
        if (isSeq<YAMLMap>(thenChilds)){
            thenChilds.items.forEach(x => childTasks.add(x))
        }

        const elseChilds = task.get("else") as YAMLSeq<YAMLMap> | undefined
        // io.kestra.plugin.core.flow special case
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
            // io.kestra.plugin.core.flow.Dag special case
            const nestedDagTaskField = task.get("task") as YAMLMap
            if(isMap(nestedDagTaskField)) {
                tasksLines = {...tasksLines, ...getTasksAndFlowableLines(lineCounter, nestedDagTaskField)}
            }
        }
    }
    return tasksLines
}