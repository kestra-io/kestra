/**
 * Docs page per empty-state `type`; an explicit `learnMore` prop wins.
 *
 * The UTM tags follow the in-app convention: `utm_campaign` is the type itself,
 * not the current route, since routes carry namespace and flow names. Add them to
 * every new entry, and keep them ahead of any `#fragment`.
 */
export const links: Record<string, string> = {
    namespaceFiles: "https://kestra.io/docs/concepts/namespace-files?utm_source=app&utm_medium=referral&utm_campaign=namespace-files",
    versionPlugin: "https://kestra.io/docs/enterprise/instance/versioned-plugins?utm_source=app&utm_medium=referral&utm_campaign=version-plugin",
    kill_switches: "https://kestra.io/docs/enterprise/instance/kill-switch?utm_source=app&utm_medium=referral&utm_campaign=kill-switches",
    announcements: "https://kestra.io/docs/enterprise/instance/announcements?utm_source=app&utm_medium=referral&utm_campaign=announcements",
    testSuites: "https://kestra.io/docs/enterprise/governance/unit-tests?utm_source=app&utm_medium=referral&utm_campaign=test-suites",
    apps: "https://kestra.io/docs/enterprise/scalability/apps?utm_source=app&utm_medium=referral&utm_campaign=apps",
    assets: "https://kestra.io/docs/enterprise/governance/assets?utm_source=app&utm_medium=referral&utm_campaign=assets",
    cases: "https://kestra.io/docs/enterprise/governance/cases?utm_source=app&utm_medium=referral&utm_campaign=cases",
    apiTokens: "https://kestra.io/docs/enterprise/auth/api-tokens?utm_source=app&utm_medium=referral&utm_campaign=api-tokens",
    panels: "https://kestra.io/docs/ui/flows?utm_source=app&utm_medium=referral&utm_campaign=panels",
    "dependencies.FLOW": "https://kestra.io/docs/ui/flows?utm_source=app&utm_medium=referral&utm_campaign=dependencies-flow#dependencies",
    "dependencies.EXECUTION": "https://kestra.io/docs/ui/flows?utm_source=app&utm_medium=referral&utm_campaign=dependencies-execution#dependencies",
    "dependencies.NAMESPACE": "https://kestra.io/docs/ui/flows?utm_source=app&utm_medium=referral&utm_campaign=dependencies-namespace#dependencies",
    "dependencies.ASSET": "https://kestra.io/docs/ui/flows?utm_source=app&utm_medium=referral&utm_campaign=dependencies-asset#dependencies",
    triggers: "https://kestra.io/docs/workflow-components/triggers?utm_source=app&utm_medium=referral&utm_campaign=triggers",
    mcpToolFlows: "https://kestra.io/docs/ai-tools/mcp-server?utm_source=app&utm_medium=referral&utm_campaign=mcp-tool-flows",
    concurrency_executions: "https://kestra.io/docs/workflow-components/concurrency?utm_source=app&utm_medium=referral&utm_campaign=concurrency-executions",
    concurrency_limit: "https://kestra.io/docs/workflow-components/concurrency?utm_source=app&utm_medium=referral&utm_campaign=concurrency-limit",
    concurrency_limits: "https://kestra.io/docs/workflow-components/concurrency?utm_source=app&utm_medium=referral&utm_campaign=concurrency-limits",
    policies: "https://kestra.io/docs/enterprise/governance/policies?utm_source=app&utm_medium=referral&utm_campaign=policies",
    credentials: "https://kestra.io/docs/enterprise/auth/credentials?utm_source=app&utm_medium=referral&utm_campaign=credentials",
    tests: "https://kestra.io/docs/enterprise/governance/unit-tests?utm_source=app&utm_medium=referral&utm_campaign=tests",
    iam: "https://kestra.io/docs/enterprise/auth?utm_source=app&utm_medium=referral&utm_campaign=iam",
    tenants: "https://kestra.io/docs/enterprise/governance/tenants?utm_source=app&utm_medium=referral&utm_campaign=tenants",
    auditlogs: "https://kestra.io/docs/enterprise/governance/audit-logs?utm_source=app&utm_medium=referral&utm_campaign=auditlogs",
    quotas: "https://kestra.io/docs/workflow-components/quotas?utm_source=app&utm_medium=referral&utm_campaign=quotas",
    instance: "https://kestra.io/docs/enterprise/instance?utm_source=app&utm_medium=referral&utm_campaign=instance",
    dashboards: "https://kestra.io/docs/ui/dashboards?utm_source=app&utm_medium=referral&utm_campaign=dashboards",
    blueprints: "https://kestra.io/docs/enterprise/governance/custom-blueprints?utm_source=app&utm_medium=referral&utm_campaign=blueprints",
    namespace: "https://kestra.io/docs/enterprise/governance/namespace-management?utm_source=app&utm_medium=referral&utm_campaign=namespace",
    variables: "https://kestra.io/docs/how-to-guides/namespace-variables-vs-kvstore?utm_source=app&utm_medium=referral&utm_campaign=variables",
    secrets: "https://kestra.io/docs/concepts/secret?utm_source=app&utm_medium=referral&utm_campaign=secrets",
    promote: "https://kestra.io/docs/enterprise/governance/promote?utm_source=app&utm_medium=referral&utm_campaign=promote",
    /** Groups have no page of their own; RBAC covers them. */
    groups: "https://kestra.io/docs/enterprise/auth/rbac?utm_source=app&utm_medium=referral&utm_campaign=groups",
}
