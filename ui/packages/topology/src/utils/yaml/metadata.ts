// Read and write the flow's root keys, keeping their canonical order.

import {
    Document,
    Pair,
    Scalar,
    YAMLMap,
    YAMLSeq,
    isMap,
    isScalar,
    isSeq,
    type Node,
} from "yaml"
import {TOSTRING_OPTIONS, parseDocumentTyped, scalarKey} from "./document.ts"

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
