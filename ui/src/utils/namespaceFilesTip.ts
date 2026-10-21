const PYTHON_SCRIPT_TYPE = "io.kestra.plugin.scripts.python.Script"

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value)
}

function isInlinePythonScript(node: Record<string, unknown>): boolean {
    if (node.type !== PYTHON_SCRIPT_TYPE) {
        return false
    }

    if (typeof node.script !== "string" || node.script.trim() === "") {
        return false
    }

    const namespaceFiles = node.namespaceFiles
    return !(isRecord(namespaceFiles) && namespaceFiles.enabled === true)
}

/**
 * Whether the parsed flow contains a Python `Script` task with an inline script that does
 * not use Namespace Files, at any nesting level (flowable tasks, errors, finally, ...).
 */
export function hasInlinePythonScript(flow: unknown): boolean {
    if (Array.isArray(flow)) {
        return flow.some(hasInlinePythonScript)
    }

    if (!isRecord(flow)) {
        return false
    }

    if (isInlinePythonScript(flow)) {
        return true
    }

    return Object.values(flow).some(hasInlinePythonScript)
}
