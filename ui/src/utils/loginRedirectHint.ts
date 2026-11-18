export interface LoginRedirectHint {
    namespace: string
    flowId: string
    executionId?: string
    tabLabelKey?: string
}

const TAB_LABEL_KEYS: Map<string, string> = new Map(Object.entries({
    "overview": "overview",
    "gantt": "gantt",
    "logs": "logs",
    "topology": "topology",
    "outputs": "outputs",
    "metrics": "metrics",
    "dependencies": "dependencies",
    "audit-logs": "auditlogs",
    "assets": "assets.title",
    "executions": "executions",
    "edit": "edit",
    "revisions": "revisions",
    "triggers": "triggers",
    "concurrency": "concurrency",
    "promote": "promote.label",
}))

const PREFIX = "^(?:/[A-Za-z0-9_-]+)?"
const NAMESPACE = "([A-Za-z0-9._-]+)"
const ID = "([A-Za-z0-9_-]+)"
const EXECUTION_PATH = new RegExp(`${PREFIX}/executions/${NAMESPACE}/${ID}/${ID}(?:/([^/]+))?/?$`)
const FLOW_PATH = new RegExp(`${PREFIX}/flows/edit/${NAMESPACE}/${ID}(?:/([^/]+))?/?$`)

const tabLabelKey = (slug: string | undefined) => slug === undefined ? undefined : TAB_LABEL_KEYS.get(slug)

export function loginRedirectHint(from: string | undefined): LoginRedirectHint | undefined {
    if (!from) return undefined

    const path = from.split(/[?#]/)[0]

    const execution = EXECUTION_PATH.exec(path)
    if (execution) {
        const [, namespace, flowId, executionId, tab] = execution
        return {namespace, flowId, executionId, tabLabelKey: tab === undefined ? TAB_LABEL_KEYS.get("overview") : tabLabelKey(tab)}
    }

    const flow = FLOW_PATH.exec(path)
    if (flow) {
        const [, namespace, flowId, tab] = flow
        return {namespace, flowId, tabLabelKey: tabLabelKey(tab)}
    }

    return undefined
}
