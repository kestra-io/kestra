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
    isScalar,
    isNode,
    visit,
    type Range,
    type ToStringOptions,
} from "yaml"
import cloneDeep from "lodash/cloneDeep"

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

function scalarKey(pair: Pair<unknown, unknown>): string | undefined {
    if (isScalar(pair.key)) {
        return typeof pair.key.value === "string" ? pair.key.value : undefined
    }
    return typeof pair.key === "string" ? pair.key : undefined
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
        yamlDoc: Document<YAMLMap<Scalar<string>, Node>>,
        section: string
    }) {
    const sectionNode = yamlDoc.contents?.items?.find(
        (e) => e.key.value === section,
    ) as { value: YAMLSeq<YAMLMap<Scalar<string>, Node>> } | undefined
    return sectionNode?.value
}

function getPathFromId({node, id} : {
    node: Node,
    id: string
}): (string | number)[] | undefined {
    if (isSeq<Node>(node)) {
        let index = 0
        for (const item of node.items) {
            if (isMap<Scalar<string>, Node>(item)) {
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

    if( isMap<Scalar<string>, Node>(node)) {
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
    return joinPath([section, ...pathArray])
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
    callback?: (element: YAMLMap<Scalar<string>, string | Node>) => Node | Document,
}) {
    function find(element?: Node): Node | Document | void {
        if (!element) {
            return
        }
        if (isMap<Scalar<string>, string | Node>(element)) {
            if (element.get("type") !== undefined && key === element.get(keyName)) {
                return callback ? callback(element) : element
            }
        }
        if (isSeq<Node>(element) || isMap<Scalar<string>, Node>(element)) {
            for (const [itemIndex, item] of element.items.entries()) {
                const result = isMap(item)
                    ? find(item)
                    : isPair(item)
                        ? find(item.value ?? undefined)
                        : undefined

                if (!result) {
                    continue
                }
                if (!callback) {
                    return result
                }
                // swapBlocks' callback yields a Document, which only reaches
                // output because Document stringifies where a node is expected
                const replacement = result as Node
                if (isMap(element) && isPair<Scalar<string>, Node>(item)) {
                    element.set(item.key, replacement)
                } else {
                    element.items[itemIndex] = replacement
                }
            }
        }
    }

    const result = find(yamlDoc)

    return result === undefined ? undefined : new Document(result)
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

function extractBlockWithPathFromDocument({yamlDoc, path}: {
    yamlDoc: Document<YAMLMap<Scalar<string>, Node>>,
    path: string,
}) {
    const element = yamlDoc.getIn(parsePath(path))
    if (element === undefined) {
        return undefined
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
    if(newContent === ""){
        yamlDoc.deleteIn(pathArray)
        return yamlDoc.toString(TOSTRING_OPTIONS)
    }
    const newItem = yamlDoc.createNode(parseDocument(newContent))
    const insertBlock = !yamlDoc.hasIn(pathArray)
    yamlDoc.setIn(pathArray, newItem)

    // When inserting a top level element
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
        Pair(_, pair) {
            if (
                scalarKey(pair) === "dependsOn" &&
                isSeq(pair.value) &&
                pair.value.items.some((e) => isScalar(e) && e.value === key1)
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
    yamlDoc: Document<YAMLMap<Scalar<string>, Node>>,
    patentNode: YAMLMap<Scalar<string>, Node>,
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

export function parsePath(path: string): (string | number)[] {
    const segments: (string | number)[] = []
    let i = 0
    while (i < path.length) {
        const ch = path[i]
        if (ch === ".") {
            i++
        } else if (ch === "[") {
            const quote = path[i + 1]
            if (quote === "\"" || quote === "'") {
                let key = ""
                let j = i + 2
                while (j < path.length && path[j] !== quote) {
                    if (path[j] === "\\" && j + 1 < path.length) {
                        key += path[j + 1]
                        j += 2
                    } else {
                        key += path[j]
                        j++
                    }
                }
                i = path[j + 1] === "]" ? j + 2 : j + 1
                segments.push(key)
            } else {
                const close = path.indexOf("]", i)
                const inner = path.slice(i + 1, close)
                segments.push(/^\d+$/.test(inner) ? parseInt(inner, 10) : inner)
                i = close + 1
            }
        } else {
            let j = i
            while (j < path.length && path[j] !== "." && path[j] !== "[") j++
            segments.push(path.slice(i, j))
            i = j
        }
    }
    return segments
}

function keyNeedsQuoting(key: string): boolean {
    return key === "" || /[.[\]"'\\]/.test(key)
}

function quoteKey(key: string): string {
    return `["${key.replace(/\\/g, "\\\\").replace(/"/g, "\\\"")}"]`
}

export function appendKeyToPath(basePath: string, key: string): string {
    if (keyNeedsQuoting(key)) return `${basePath}${quoteKey(key)}`
    return basePath ? `${basePath}.${key}` : key
}

export function joinPath(segments: (string | number)[]): string {
    let out = ""
    for (const seg of segments) {
        if (typeof seg === "number") out += `[${seg}]`
        else if (keyNeedsQuoting(seg)) out += quoteKey(seg)
        else out += out ? `.${seg}` : seg
    }
    return out
}

export function pruneEmptySequences(source: string): string {
    const doc = parseDocumentTyped(source)
    visit(doc, {
        Pair(_key, pair) {
            if (isSeq(pair.value) && pair.value.items.length === 0) {
                return visit.REMOVE
            }
            return undefined
        },
    })
    return doc.toString(TOSTRING_OPTIONS)
}

function hasQuotedSegment(path: string): boolean {
    return path.includes("[\"") || path.includes("['")
}

function lastSegmentKey(path: string): string {
    return hasQuotedSegment(path)
        ? String(parsePath(path).at(-1))
        : (path.split(".").pop() as string)
}

function getParentNode(yamlDoc: ReturnType<typeof parseDocumentTyped>, parentPath: string) {
    if (hasQuotedSegment(parentPath)) {
        const segments = parsePath(parentPath)
        if (segments.length <= 1) {
            if (!yamlDoc.contents) {
                throw new Error(`Document is empty, cannot insert block with path ${parentPath}`)
            }
            return yamlDoc.contents
        }
        const parentPathWithoutKey = joinPath(segments.slice(0, -1))
        const parentNode = yamlDoc.getIn(parsePath(parentPathWithoutKey)) as YAMLMap<Scalar<string>, Node>
        if (!parentNode) {
            const newParentNode = createParentNode(parentPath)
            const parentParentNode = getParentNode(yamlDoc, parentPathWithoutKey)
            parentParentNode?.items.push(newParentNode)
            return newParentNode.value
        }
        return parentNode
    }
    if(!parentPath.includes(".")){
        if(!yamlDoc.contents){
            throw new Error(`Document is empty, cannot insert block with path ${parentPath}`)
        }
        return yamlDoc.contents
    } else {
        const parentPathWithoutKey = parentPath.substring(0, parentPath.lastIndexOf("."))
        const parentNode = yamlDoc.getIn(parsePath(parentPathWithoutKey)) as YAMLMap<Scalar<string>, Node>
        if (!parentNode) {
            const newParentNode = createParentNode(parentPathWithoutKey)
            const parentParentNode = getParentNode(yamlDoc, parentPathWithoutKey)
            parentParentNode?.items.push(newParentNode)
            return newParentNode.value
        }
        return parentNode
    }
}

function createParentNode(parentPath: string) {
    const newParentNode = new YAMLSeq()
    const parentKey = lastSegmentKey(parentPath)
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

    const parentNode = yamlDoc.getIn(parsedPath) as YAMLMap<Scalar<string>, Node>

    if (!parentNode) {
        const newPairNode = createPairNode(lastSegmentKey(parentPath), newPropNode)
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
        Pair(_, pair) {
            if (scalarKey(pair) === section && isNode(pair.value)) {
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
    const yamlDoc = parseDocumentTyped(source)

    if (!isMap(yamlDoc.contents)) {
        return source
    }

    for (const property in metadata) {
        const existing = yamlDoc.contents.items.find((item) => scalarKey(item) === property)
        if (existing) {
            existing.value = metadata[property]
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
        const item = yamlDoc.contents?.items.find((e) => scalarKey(e) === prop)
        if (item?.value && isItemTruthy(item.value)) {
            updatedItems.push(item)
            if(isSeq<Node>(item.value)) {
                if (!item.key.commentBefore) {
                    item.key.spaceBefore = true
                }
                item.value.items.forEach((seqItem, index) => {
                    if(index === 0) {
                        return
                    }
                    seqItem.spaceBefore = true
                })

            }
        }
    }
    yamlDoc.contents.items = updatedItems
    return yamlDoc
}

export function getMetadata(source: string): Record<string, any> {
    const contents = parseDocumentTyped(source).contents
    if (!isMap(contents)) return {}
    const metadata: Record<string, any> = {}

    for (const item of contents.items) {
        const key = scalarKey(item)
        if (key === undefined || (FLOW_SECTION_KEYS as readonly string[]).includes(key)) {
            continue
        }
        metadata[key] =
            isMap(item.value) || isSeq(item.value)
                ? item.value.toJSON()
                : isScalar(item.value)
                    ? item.value.value
                    : undefined
    }
    return metadata
}

export function deleteMetadata(source: string, metadata: string) {
    const yamlDoc = parseDocumentTyped(source)

    if (!isMap(yamlDoc.contents)) {
        return source
    }

    const items = yamlDoc.contents.items
    const item = items.find((e) => scalarKey(e) === metadata)
    if (item) {
        items.splice(items.indexOf(item), 1)
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

function isChildrenOf(source: string, section: string, parentKey: string, childKey: string, keyName: string) {
    const {sectionNode} = getSectionNodeAndDocumentFromSource({source, section})
    if (!sectionNode) return false

    const parentDoc = extractBlockFromDocument({yamlDoc: sectionNode, keyName, key: parentKey})
    if (!parentDoc) return false

    let result = false
    visit(parentDoc, {
        Map(_, map) {
            if (map.get(keyName) === childKey) {
                result = true
                return visit.BREAK
            }
        },
    })
    return result
}

export function isParentChildrenRelation({source, sections, key1, key2, keyName}: {
    source: string;
    sections: string[];
    key1: string;
    key2: string;
    keyName: string;
}) {
    if (!keyName) keyName = "id"
    return sections.reduce(
        (acc, section) =>
            acc ||
            isChildrenOf(source, section, key2, key1, keyName) ||
            isChildrenOf(source, section, key1, key2, keyName),
        false,
    )
}

export type FieldMatch<T extends string> = Record<T, any> & {range: Range}

function pathOfPairs(ancestry: readonly (Document | Node | Pair)[]): string {
    return ancestry
        .filter((node) => isPair(node))
        .map((node) => scalarKey(node) ?? "")
        .join(".")
}

export function extractFieldFromMaps<T extends string>(
    source: string,
    fieldName: T,
    parentPathPredicate: (path: string) => boolean = () => true,
    valuePredicate: (value: unknown) => boolean = () => true,
    keepEmptyFields: boolean = false,
): FieldMatch<T>[] {
    const yamlDoc = parseDocumentTyped(source)
    const maps: FieldMatch<T>[] = []
    visit(yamlDoc, {
        Map(_, map, parent) {
            if (!map.range || !parentPathPredicate(pathOfPairs(parent))) {
                return
            }
            let matched = false
            for (const item of map.items) {
                if (scalarKey(item) !== fieldName) {
                    continue
                }
                const value = item.value
                const fieldValue =
                    (isScalar(value) ? value.value : undefined) ??
                    (isSeq(value) || isMap(value) ? value.items : undefined)
                if (valuePredicate(fieldValue)) {
                    maps.push({[fieldName]: fieldValue, range: map.range} as FieldMatch<T>)
                    matched = true
                }
            }
            if (!matched && keepEmptyFields) {
                maps.push({[fieldName]: undefined, range: map.range} as FieldMatch<T>)
            }
        },
    })
    return maps
}

export interface TypedBlock {
    type: string;
    value: Record<string, any>;
    range: Range;
    path: string;
}

export function extractTypedBlocks(source: string): TypedBlock[] {
    return extractTypedBlocksWithMeta(source).blocks
}

export interface FlowSourceData {
    blocks: TypedBlock[];
    namespace?: string;
    id?: string;
}

export function extractTypedBlocksWithMeta(source: string): FlowSourceData {
    const yamlDoc = parseDocumentTyped(source)
    const blocks: TypedBlock[] = []
    visit(yamlDoc, {
        Map(_, map, parents) {
            const typeNode = map.items.find((item) => scalarKey(item) === "type")?.value
            const type = isScalar(typeNode) ? typeNode.value : undefined
            if (typeof type === "string" && map.range) {
                blocks.push({
                    type,
                    value: map.toJSON(),
                    range: map.range,
                    path: pathOfPairs(parents),
                })
            }
        },
    })
    const root = yamlDoc.contents
    const namespace = isMap(root) ? root.get("namespace") : undefined
    const id = isMap(root) ? root.get("id") : undefined
    return {
        blocks,
        namespace: typeof namespace === "string" ? namespace : undefined,
        id: typeof id === "string" ? id : undefined,
    }
}

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