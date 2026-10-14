export interface LoginRedirectHint {
    namespace: string
    flowId: string
    executionId?: string
    tab?: string
}

const EXECUTION_PATH = /(?:^|\/)executions\/([^/?#]+)\/([^/?#]+)\/([^/?#]+)(?:\/([^/?#]+))?/
const FLOW_PATH = /(?:^|\/)flows\/edit\/([^/?#]+)\/([^/?#]+)(?:\/([^/?#]+))?/

export function loginRedirectHint(path: string | undefined): LoginRedirectHint | undefined {
    if (!path) return undefined

    const execution = EXECUTION_PATH.exec(path)
    if (execution) {
        const [, namespace, flowId, executionId, tab] = execution
        return {namespace, flowId, executionId, tab}
    }

    const flow = FLOW_PATH.exec(path)
    if (flow) {
        const [, namespace, flowId, tab] = flow
        return {namespace, flowId, tab}
    }

    return undefined
}
