// Shared yaml-document primitives used by the other modules in this folder.

import {
    Document,
    Pair,
    Scalar,
    YAMLMap,
    isScalar,
    parseDocument,
    type Node,
    type ToStringOptions,
} from "yaml"

export function parseDocumentTyped(source: string) {
    return parseDocument(source) as Document<YAMLMap<Scalar<string>, Node>>
}

export function scalarKey(pair: Pair<unknown, unknown>): string | undefined {
    if (isScalar(pair.key)) {
        return typeof pair.key.value === "string" ? pair.key.value : undefined
    }
    return typeof pair.key === "string" ? pair.key : undefined
}

export const TOSTRING_OPTIONS: ToStringOptions = {
    lineWidth: 0,
}
