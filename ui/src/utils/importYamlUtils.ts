import {flowYamlUtils} from "@kestra-io/topology"

export type ImportErrorCode = "empty" | "invalid_mapping" | "parse_error" | "too_large"

export interface ParseImportResult {
    errorCode?: ImportErrorCode
    parseMessage?: string
    id?: string
    namespace?: string
}

export function parseImportYaml(yaml: string): ParseImportResult {
    if (!yaml.trim()) {
        return {errorCode: "empty"}
    }

    let parsed: unknown
    try {
        parsed = flowYamlUtils.parse(yaml)
    } catch (e: unknown) {
        const parseMessage = e instanceof Error ? e.message : String(e)
        return {errorCode: "parse_error", parseMessage}
    }

    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
        return {errorCode: "invalid_mapping"}
    }

    const record = parsed as Record<string, unknown>
    return {
        id: typeof record.id === "string" ? record.id : undefined,
        namespace: typeof record.namespace === "string" ? record.namespace : undefined,
    }
}
