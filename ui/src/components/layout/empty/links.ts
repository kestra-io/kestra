/**
 * Docs page per empty-state `type`; an explicit `learnMore` prop wins.
 *
 * The UTM tags follow the in-app convention: `utm_campaign` is the type itself,
 * not the current route, since routes carry namespace and flow names. Add them to
 * every new entry, and keep them ahead of any `#fragment`.
 */
export const links: Record<string, string> = {
    namespaceFiles: "https://kestra.io/docs/concepts/namespace-files?utm_source=kestra_app&utm_medium=referral&utm_campaign=namespace_files&utm_content=learn_more",
    versionPlugin: "https://kestra.io/docs/enterprise/instance/versioned-plugins?utm_source=kestra_app&utm_medium=referral&utm_campaign=version_plugin&utm_content=learn_more",
    kill_switches: "https://kestra.io/docs/enterprise/instance/kill-switch?utm_source=kestra_app&utm_medium=referral&utm_campaign=kill_switches&utm_content=learn_more",
    announcements: "https://kestra.io/docs/enterprise/instance/announcements?utm_source=kestra_app&utm_medium=referral&utm_campaign=announcements&utm_content=learn_more",
    testSuites: "https://kestra.io/docs/enterprise/governance/unit-tests?utm_source=kestra_app&utm_medium=referral&utm_campaign=test_suites&utm_content=learn_more",
    apps: "https://kestra.io/docs/enterprise/scalability/apps?utm_source=kestra_app&utm_medium=referral&utm_campaign=apps&utm_content=learn_more",
    assets: "https://kestra.io/docs/enterprise/governance/assets?utm_source=kestra_app&utm_medium=referral&utm_campaign=assets&utm_content=learn_more",
    cases: "https://kestra.io/docs/enterprise/governance/cases?utm_source=kestra_app&utm_medium=referral&utm_campaign=cases&utm_content=learn_more",
    apiTokens: "https://kestra.io/docs/enterprise/auth/api-tokens?utm_source=kestra_app&utm_medium=referral&utm_campaign=api_tokens&utm_content=learn_more",
    panels: "https://kestra.io/docs/ui/flows?utm_source=kestra_app&utm_medium=referral&utm_campaign=panels&utm_content=learn_more",
    "dependencies.FLOW": "https://kestra.io/docs/ui/flows?utm_source=kestra_app&utm_medium=referral&utm_campaign=dependencies_flow&utm_content=learn_more#dependencies",
    "dependencies.EXECUTION": "https://kestra.io/docs/ui/flows?utm_source=kestra_app&utm_medium=referral&utm_campaign=dependencies_execution&utm_content=learn_more#dependencies",
    "dependencies.NAMESPACE": "https://kestra.io/docs/ui/flows?utm_source=kestra_app&utm_medium=referral&utm_campaign=dependencies_namespace&utm_content=learn_more#dependencies",
    "dependencies.ASSET": "https://kestra.io/docs/ui/flows?utm_source=kestra_app&utm_medium=referral&utm_campaign=dependencies_asset&utm_content=learn_more#dependencies",
    triggers: "https://kestra.io/docs/workflow-components/triggers?utm_source=kestra_app&utm_medium=referral&utm_campaign=triggers&utm_content=learn_more",
    mcpToolFlows: "https://kestra.io/docs/ai-tools/mcp-server?utm_source=kestra_app&utm_medium=referral&utm_campaign=mcp_tool_flows&utm_content=learn_more",
    concurrency_executions: "https://kestra.io/docs/workflow-components/concurrency?utm_source=kestra_app&utm_medium=referral&utm_campaign=concurrency_executions&utm_content=learn_more",
    concurrency_limit: "https://kestra.io/docs/workflow-components/concurrency?utm_source=kestra_app&utm_medium=referral&utm_campaign=concurrency_limit&utm_content=learn_more",
    concurrency_limits: "https://kestra.io/docs/workflow-components/concurrency?utm_source=kestra_app&utm_medium=referral&utm_campaign=concurrency_limits&utm_content=learn_more",
    policies: "https://kestra.io/docs/enterprise/governance/policies?utm_source=kestra_app&utm_medium=referral&utm_campaign=policies&utm_content=learn_more",
    tests: "https://kestra.io/docs/enterprise/governance/unit-tests?utm_source=kestra_app&utm_medium=referral&utm_campaign=tests&utm_content=learn_more",
    iam: "https://kestra.io/docs/enterprise/auth?utm_source=kestra_app&utm_medium=referral&utm_campaign=iam&utm_content=learn_more",
    tenants: "https://kestra.io/docs/enterprise/governance/tenants?utm_source=kestra_app&utm_medium=referral&utm_campaign=tenants&utm_content=learn_more",
    auditlogs: "https://kestra.io/docs/enterprise/governance/audit-logs?utm_source=kestra_app&utm_medium=referral&utm_campaign=auditlogs&utm_content=learn_more",
    quotas: "https://kestra.io/docs/workflow-components/quotas?utm_source=kestra_app&utm_medium=referral&utm_campaign=quotas&utm_content=learn_more",
    instance: "https://kestra.io/docs/enterprise/instance?utm_source=kestra_app&utm_medium=referral&utm_campaign=instance&utm_content=learn_more",
    blueprints: "https://kestra.io/docs/enterprise/governance/custom-blueprints?utm_source=kestra_app&utm_medium=referral&utm_campaign=blueprints&utm_content=learn_more",
    namespace: "https://kestra.io/docs/enterprise/governance/namespace-management?utm_source=kestra_app&utm_medium=referral&utm_campaign=namespace&utm_content=learn_more",
    variables: "https://kestra.io/docs/how-to-guides/namespace-variables-vs-kvstore?utm_source=kestra_app&utm_medium=referral&utm_campaign=variables&utm_content=learn_more",
    secrets: "https://kestra.io/docs/concepts/secret?utm_source=kestra_app&utm_medium=referral&utm_campaign=secrets&utm_content=learn_more",
    /** @todo Use a dedicated promotion docs page once one exists. */
    promote: "https://kestra.io/docs/best-practices/from-dev-to-prod?utm_source=kestra_app&utm_medium=referral&utm_campaign=promote&utm_content=learn_more",
    /** Groups have no page of their own; RBAC covers them. */
    groups: "https://kestra.io/docs/enterprise/auth/rbac?utm_source=kestra_app&utm_medium=referral&utm_campaign=groups&utm_content=learn_more",
}
