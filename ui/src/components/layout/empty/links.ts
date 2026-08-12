/**
 * Documentation page offered by each empty state, keyed by the `Empty`
 * component's `type` prop. An explicit `learnMore` prop wins over this registry.
 *
 * Empty states point at the docs rather than at a recording, since the docs
 * pages already embed the relevant video.
 *
 * @todo Point every entry at a short.io link so the target can be changed
 * without a release.
 */
export const links: Record<string, string> = {
    namespaceFiles: "https://kestra.io/docs/concepts/namespace-files",
    versionPlugin: "https://kestra.io/docs/enterprise/instance/versioned-plugins",
    kill_switches: "https://kestra.io/docs/enterprise/instance/kill-switch",
    announcements: "https://kestra.io/docs/enterprise/instance/announcements",
    testSuites: "https://kestra.io/docs/enterprise/governance/unit-tests",
    apps: "https://kestra.io/docs/enterprise/scalability/apps",
    assets: "https://kestra.io/docs/enterprise/governance/assets",
    cases: "https://kestra.io/docs/enterprise/governance/cases",
    apiTokens: "https://kestra.io/docs/enterprise/auth/api-tokens",
    panels: "https://kestra.io/docs/ui/flows",
    "dependencies.FLOW": "https://kestra.io/docs/ui/flows#dependencies",
    "dependencies.EXECUTION": "https://kestra.io/docs/ui/flows#dependencies",
    "dependencies.NAMESPACE": "https://kestra.io/docs/ui/flows#dependencies",
    "dependencies.ASSET": "https://kestra.io/docs/ui/flows#dependencies",
    triggers: "https://kestra.io/docs/workflow-components/triggers",
    mcpToolFlows: "https://kestra.io/docs/ai-tools/mcp-server",
    concurrency_executions: "https://kestra.io/docs/workflow-components/concurrency",
    concurrency_limit: "https://kestra.io/docs/workflow-components/concurrency",
    concurrency_limits: "https://kestra.io/docs/workflow-components/concurrency",
    policies: "https://kestra.io/docs/enterprise/governance/policies",
    tests: "https://kestra.io/docs/enterprise/governance/unit-tests",
    iam: "https://kestra.io/docs/enterprise/auth",
    tenants: "https://kestra.io/docs/enterprise/governance/tenants",
    auditlogs: "https://kestra.io/docs/enterprise/governance/audit-logs",
    quotas: "https://kestra.io/docs/workflow-components/quotas",
    instance: "https://kestra.io/docs/enterprise/instance",
    blueprints: "https://kestra.io/docs/enterprise/governance/custom-blueprints",
    namespace: "https://kestra.io/docs/enterprise/governance/namespace-management",
    variables: "https://kestra.io/docs/how-to-guides/namespace-variables-vs-kvstore",
    secrets: "https://kestra.io/docs/concepts/secret",
    /** @todo Use a dedicated promotion docs page once one exists. */
    promote: "https://kestra.io/docs/best-practices/from-dev-to-prod",
    /** Groups have no page of their own; RBAC covers them. */
    groups: "https://kestra.io/docs/enterprise/auth/rbac",
}
