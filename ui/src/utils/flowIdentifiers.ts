const FLOW_ID_PATTERN = /^[a-zA-Z0-9][a-zA-Z0-9._-]*$/
const FLOW_ID_MAX_LENGTH = 100

const NAMESPACE_PATTERN = /^[a-z0-9][a-z0-9._-]*$/
const NAMESPACE_MAX_LENGTH = 150

export function isValidFlowId(id: string): boolean {
    return id.length <= FLOW_ID_MAX_LENGTH && FLOW_ID_PATTERN.test(id)
}

export function isValidNamespace(namespace: string): boolean {
    return namespace.length <= NAMESPACE_MAX_LENGTH && NAMESPACE_PATTERN.test(namespace)
}
