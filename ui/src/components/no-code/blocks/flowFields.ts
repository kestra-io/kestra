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
    "workerSelector",
    "disabled",
]

// quotas is EE-only: on OSS the executor rejects it at runtime in a way that
// crash-loops the server, so the no-code editor must not offer a way to add it.
export function getFlowFields(edition?: string): string[] {
    return edition === "OSS" ? BASE_FLOW_FIELDS.filter((key) => key !== "quotas") : BASE_FLOW_FIELDS
}
