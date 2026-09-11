// Read and mutate task, trigger and pluginDefaults blocks in a flow source.

import {
    Document,
    Pair,
    Scalar,
    YAMLMap,
    YAMLSeq,
    isCollection,
    isMap,
    isNode,
    isPair,
    isScalar,
    isSeq,
    parseDocument,
    visit,
    type Node,
} from "yaml"
import {TOSTRING_OPTIONS, parseDocumentTyped, scalarKey} from "./document.ts"
import {hasQuotedSegment, joinPath, lastSegmentKey, parsePath} from "./paths.ts"
import {sortPredicate} from "./serialization.ts"

function getSectionNodeAndDocumentFromSource({source, section}: {
    source: string,
    section: string
}) {
    const yamlDoc = parseDocumentTyped(source)
    const sectionNode = getSectionFromDocument({yamlDoc, section})
    return {yamlDoc, sectionNode}
}

function getSectionFromDocument({yamlDoc, section}:
    {
        yamlDoc: Document<YAMLMap<Scalar<string>, Node>>,
        section: string
    }) {
    const value = yamlDoc.contents?.items?.find((e) => scalarKey(e) === section)?.value
    return isSeq<YAMLMap<Scalar<string>, Node>>(value) ? value : undefined
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
    parentNode: YAMLSeq<unknown>,
    parentPath: (string|number)[],
    refPath?: string | number,
    position: "before" | "after" = "after",
) {
    if (refPath === undefined) {
        return position === "before" ? 0 : parentNode.items.length - 1
    }

    return parentNode.items.indexOf(yamlDoc.getIn([...parentPath, refPath]))
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

function nodeKind(node: unknown): string {
    if (isMap(node)) return "mapping"
    if (isScalar(node)) return "scalar"
    return "value"
}

type ParentCollection = YAMLMap<Scalar<string>, Node> | YAMLSeq<unknown>

function getParentNode(
    yamlDoc: ReturnType<typeof parseDocumentTyped>,
    parentPath: string,
): ParentCollection {
    const parentPathWithoutKey = hasQuotedSegment(parentPath)
        ? joinPath(parsePath(parentPath).slice(0, -1))
        : parentPath.substring(0, parentPath.lastIndexOf("."))

    if (parentPathWithoutKey === "") {
        if (!yamlDoc.contents) {
            throw new Error(`Document is empty, cannot insert block with path ${parentPath}`)
        }
        return yamlDoc.contents
    }

    const parentNode = yamlDoc.getIn(parsePath(parentPathWithoutKey))
    if (isCollection<Node>(parentNode)) {
        return parentNode
    }
    // an empty `tasks:` parses to null, which still wants the parent created
    if (parentNode !== undefined && parentNode !== null) {
        throw new Error(
            `Cannot insert a block at path ${parentPath}: ${parentPathWithoutKey} holds a ${nodeKind(parentNode)}, not a collection.`,
        )
    }

    const newParentSeq = new YAMLSeq<unknown>()
    attachSequence(
        getParentNode(yamlDoc, parentPathWithoutKey),
        lastSegmentKey(parentPathWithoutKey),
        newParentSeq,
    )
    return newParentSeq
}

// a key written with no value (`tasks:`) parses to a pair holding null, so
// pushing a new pair for it would leave the document carrying the key twice
function attachSequence(container: ParentCollection, key: string, seq: YAMLSeq<unknown>) {
    const existing = isMap(container)
        ? container.items.find((item) => scalarKey(item) === key)
        : undefined

    if (existing) {
        existing.value = seq
    } else {
        container.items.push(new Pair(new Scalar(key), seq))
    }
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
    const newPropNode = yamlDoc.createNode(parseDocument(newBlock)) as Node

    const parsedPath = parsePath(parentPath)

    const parentNode = yamlDoc.getIn(parsedPath)

    if (!parentNode) {
        const seq = new YAMLSeq<unknown>()
        seq.add(newPropNode)
        attachSequence(getParentNode(yamlDoc, parentPath), lastSegmentKey(parentPath), seq)
        return yamlDoc.toString(TOSTRING_OPTIONS)
    }

    if (!isSeq<unknown>(parentNode)) {
        throw new Error(`Cannot insert a block at path ${parentPath}: that path holds a ${nodeKind(parentNode)}, not a sequence.`)
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
