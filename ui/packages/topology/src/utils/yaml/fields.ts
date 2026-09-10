// Structural extraction: every map carrying a given field, every typed block.

import {
    Document,
    Pair,
    isMap,
    isPair,
    isScalar,
    isSeq,
    visit,
    type Node,
    type Range,
} from "yaml"
import {parseDocumentTyped, scalarKey} from "./document.ts"

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
