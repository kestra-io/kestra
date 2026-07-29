/** External resources shown on an empty state: a video and a "Learn more" documentation page. */
export interface EmptyLinks {
    video?: string;
    learnMore?: string;
}

/**
 * Registry of {@link EmptyLinks} keyed by the `Empty` component's `type` prop.
 *
 * `Empty.vue` falls back to this registry when no explicit `video` /
 * `learnMore` prop is passed; an explicit prop always wins.
 */
export const links: Record<string, EmptyLinks> = {
    namespaceFiles: {
        video: "https://www.youtube.com/watch?v=BeQNI2XRddA",
        learnMore: "https://kestra.io/docs/concepts/namespace-files",
    },
    versionPlugin: {
        video: "https://www.youtube.com/watch?v=h-vmMGlTGM8&t=79s",
        learnMore: "https://kestra.io/docs/enterprise/instance/versioned-plugins",
    },
    kill_switches: {
        video: "https://youtu.be/LjiEmutGdNY",
        learnMore: "https://kestra.io/docs/enterprise/instance/kill-switch",
    },
    announcements: {
        video: "https://www.youtube.com/watch?v=2QqGABneiNI&t=5s",
        learnMore: "https://kestra.io/docs/enterprise/instance/announcements",
    },
    testSuites: {
        video: "https://www.youtube.com/watch?v=jMZ9Cs3xxpo",
        learnMore: "https://kestra.io/docs/enterprise/governance/unit-tests",
    },
    apps: {
        video: "https://www.youtube.com/watch?v=KwBO8mcS3kk",
        learnMore: "https://kestra.io/docs/enterprise/scalability/apps",
    },
    assets: {
        video: "https://www.youtube.com/watch?v=XhICXP_GXic",
        learnMore: "https://kestra.io/docs/enterprise/governance/assets",
    },
    apiTokens: {
        video: "https://www.youtube.com/watch?v=g-740VZLRdA",
        learnMore: "https://kestra.io/docs/enterprise/auth/api-tokens",
    },
    panels: {
        video: "https://www.youtube.com/watch?v=SGlzRmJqFBI",
        learnMore: "https://kestra.io/docs/ui/flows",
    },
    "dependencies.FLOW": {
        learnMore: "https://kestra.io/docs/ui/flows#dependencies",
    },
    "dependencies.EXECUTION": {
        learnMore: "https://kestra.io/docs/ui/flows#dependencies",
    },
    "dependencies.NAMESPACE": {
        learnMore: "https://kestra.io/docs/ui/flows#dependencies",
    },
    "dependencies.ASSET": {
        learnMore: "https://kestra.io/docs/ui/flows#dependencies",
    },
    triggers: {
        video: "https://www.youtube.com/watch?v=qDiQtsVEETs",
        learnMore: "https://kestra.io/docs/workflow-components/triggers",
    },
    mcpToolFlows: {
        video: "https://www.youtube.com/watch?v=QxaMnGuu0kI",
        learnMore: "https://kestra.io/docs/ai-tools/mcp-server",
    },
    concurrency_executions: {
        video: "https://www.youtube.com/watch?v=lDGOqqMyQEo",
        learnMore: "https://kestra.io/docs/workflow-components/concurrency",
    },
    concurrency_limit: {
        video: "https://www.youtube.com/watch?v=lDGOqqMyQEo",
        learnMore: "https://kestra.io/docs/workflow-components/concurrency",
    },
    concurrency_limits: {
        video: "https://www.youtube.com/watch?v=lDGOqqMyQEo",
        learnMore: "https://kestra.io/docs/workflow-components/concurrency",
    },
    policies: {
        video: "https://www.youtube.com/watch?v=9zQTUeL0KMc",
        learnMore: "https://kestra.io/docs/enterprise/governance/policies",
    },
    tests: {
        video: "https://www.youtube.com/watch?v=jMZ9Cs3xxpo",
        learnMore: "https://kestra.io/docs/enterprise/governance/unit-tests",
    },
    iam: {
        video: "https://www.youtube.com/watch?v=9I87QZJPl1Y",
        learnMore: "https://kestra.io/docs/enterprise/auth",
    },
    tenants: {
        video: "https://www.youtube.com/watch?v=z4uzAyjKeoc",
        learnMore: "https://kestra.io/docs/enterprise/governance/tenants",
    },
    auditlogs: {
        video: "https://www.youtube.com/watch?v=Qz24gBPGZHs",
        learnMore: "https://kestra.io/docs/enterprise/governance/audit-logs",
    },
    quotas: {
        learnMore: "https://kestra.io/docs/workflow-components/quotas",
    },
    instance: {
        video: "https://www.youtube.com/watch?v=pcC3OAJPQao",
        learnMore: "https://kestra.io/docs/enterprise/instance",
    },
    blueprints: {
        video: "https://www.youtube.com/watch?v=qbGfK-FJi6s",
        learnMore: "https://kestra.io/docs/enterprise/governance/custom-blueprints",
    },
    namespace: {
        learnMore: "https://kestra.io/docs/enterprise/governance/namespace-management",
    },
    variables: {
        video: "https://www.youtube.com/watch?v=fs86GLg-OGM",
        learnMore: "https://kestra.io/docs/how-to-guides/namespace-variables-vs-kvstore",
    },
}
