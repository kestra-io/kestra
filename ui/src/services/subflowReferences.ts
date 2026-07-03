import {isScalar, parseDocument, Scalar, visit, type Node, type YAMLMap} from "yaml"

const SUBFLOW_TASK_TYPES = new Set<string>([
    "io.kestra.plugin.core.flow.Subflow",
    "io.kestra.plugin.core.flow.ForEachItem",
    "io.kestra.core.tasks.flows.Subflow",
    "io.kestra.core.tasks.flows.ForEachItem",
])

export interface SubflowLink {
    range: [number, number]
    target: {namespace: string; flowId: string}
}

function scalarString(node: unknown): string | undefined {
    return isScalar(node) && typeof node.value === "string" ? node.value : undefined
}

function isTemplated(value: string): boolean {
    return value.includes("{{")
}

function valueRange(node: unknown): [number, number] | undefined {
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

export function resolveSubflowLinks(source: string): SubflowLink[] {
    const links: SubflowLink[] = []

    let document
    try {
        document = parseDocument(source)
    } catch {
        return links
    }
    if (!document?.contents) {
        return links
    }

    visit(document, {
        Map(_key, node: YAMLMap) {
            const type = scalarString(node.get("type", true) as Node)
            if (type === undefined || !SUBFLOW_TASK_TYPES.has(type)) {
                return
            }

            const flowIdNode = node.get("flowId", true) as Node
            const namespace = scalarString(node.get("namespace", true) as Node)
            const flowId = scalarString(flowIdNode)
            if (!namespace || !flowId) {
                return
            }
            if (isTemplated(namespace) || isTemplated(flowId)) {
                return
            }

            const flowIdRange = valueRange(flowIdNode)
            if (flowIdRange) {
                links.push({range: flowIdRange, target: {namespace, flowId}})
            }
        },
    })

    return links
}
