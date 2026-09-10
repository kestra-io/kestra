export const BASE_FLOW_FIELDS = [
    "id",
    "namespace",
    "description",
    "labels",
    "inputs",
    "variables",
    "outputs",
    "concurrency",
    "retry",
    "sla",
    "checks",
    "quotas",
    "policyRefs",
    "workerSelector",
    "disabled",
]

// quotas and policyRefs are EE-only: on OSS the executor rejects quotas at runtime in a way
// that crash-loops the server, and policyRefs is parsed but silently ignored — so the no-code
// editor must not offer a way to add either on OSS.
const OSS_EXCLUDED_FIELDS = ["quotas", "policyRefs"]

export function getFlowFields(edition?: string): string[] {
    return edition === "OSS" ? BASE_FLOW_FIELDS.filter((key) => !OSS_EXCLUDED_FIELDS.includes(key)) : BASE_FLOW_FIELDS
}
