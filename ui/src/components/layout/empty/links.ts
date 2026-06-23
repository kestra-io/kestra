export interface EmptyLinks {
    video?: string;
    learnMore?: string;
}

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
        learnMore: "https://kestra.io/docs/workflow-components/triggers/mcp-tool-trigger",
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
    pluginDefaults: {
        video: "https://www.youtube.com/watch?v=9zQTUeL0KMc",
        learnMore: "https://kestra.io/docs/workflow-components/plugin-defaults",
    },
    variables: {
        video: "https://www.youtube.com/watch?v=fs86GLg-OGM",
        learnMore: "https://kestra.io/docs/how-to-guides/namespace-variables-vs-kvstore",
    },
}
