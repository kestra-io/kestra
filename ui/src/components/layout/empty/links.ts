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
 *
 * Only add a `video` once a recording covers that feature on its own. Entries
 * without one render `learnMore` as a "Learn more" button instead of the arrow
 * link, so a feature is never advertised with a video about something else.
 *
 * @todo Point every `learnMore` at a short.io link so the target can be swapped
 * between docs and a video without a release, and so clicks are tracked.
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
        // The only recording is the 1.3 release round-up, not a Kill Switch walkthrough.
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
    cases: {
        learnMore: "https://kestra.io/docs/enterprise/governance/cases",
    },
    apiTokens: {
        video: "https://www.youtube.com/watch?v=g-740VZLRdA",
        learnMore: "https://kestra.io/docs/enterprise/auth/api-tokens",
    },
    panels: {
        // The No Code recording is about building flows, not the editor panels.
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
        // The only recording covers Plugin Defaults, which is a different feature.
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
    secrets: {
        video: "https://www.youtube.com/watch?v=u0yuOYG-qMI",
        learnMore: "https://kestra.io/docs/concepts/secret",
    },
    /** @todo Swap in a dedicated promotion docs page once one is published. */
    promote: {
        // The placeholder recording here was the Assets one, unrelated to promotion.
        learnMore: "https://kestra.io/docs/best-practices/from-dev-to-prod",
    },
    groups: {
        // Groups have no page of their own; RBAC covers them alongside role bindings.
        learnMore: "https://kestra.io/docs/enterprise/auth/rbac",
    },
}
