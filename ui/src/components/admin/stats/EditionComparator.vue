<template>
    <el-row :gutter="32">
        <el-col :xs="24" :md="8" v-for="characteristics in editionCharacteristics" :key="characteristics.name" class="edition-col">
            <EditionCharacteristics
                class="h-100"
                :name="characteristics.name"
                :price="characteristics.price"
                :features="characteristics.features"
                :button="characteristics.button"
            />
        </el-col>
    </el-row>
</template>
<script setup lang="ts">
    import EditionCharacteristics from "./EditionCharacteristics.vue";

    type EditionButton = {
        text: string;
        href?: string;
    };

    type EditionCharacteristicsType = {
        name: string;
        price: string;
        features: string[];
        button: EditionButton;
    };

    const editionCharacteristics: EditionCharacteristicsType[] = [
        {
            name: "Open-Source Edition",
            price: "FREE",
            features: [
                "Apache 2.0 licence",
                "Powerful orchestration engine",
                "Declarative workflows as code and from the UI",
                "Event-driven automations",
                "Embedded code editor",
                "Embedded documentation",
                "Live-updating topology view",
                "Access to 900+ plugins",
                "Git & CI/CD integrations",
                "Basic authentication",
                "AI Copilot (Gemini models only)",
                "AI Agents",
                "Playground for iterative workflow development",
                "Key-Value Store",
                "Custom Dashboards",
                "Multi-Panel Editor with No-Code Forms",
                "Community support"
            ],
            button: {
                text: "Current version"
            }
        },
        {
            name: "Enterprise Edition",
            price: "Custom price",
            features: [
                "All features from the Open-Source Edition",
                "Full infrastructure control and customization",
                "Scalable architecture with High-Availability",
                "Multi-tenancy",
                "Remote Worker Groups for custom distributed workers",
                "Customizable backend with full infrastructure access",
                "Custom secrets backend (AWS Secret Manager, Azure Key Vault, Elasticsearch, Google Secret Manager, Hashicorp Vault)",
                "Read-only secrets backends",
                "Audit logs",
                "Single Sign-On (SSO)",
                "SCIM Directory Sync",
                "LDAP integration",
                "Role-Based Access Control (RBAC)",
                "Custom blueprints",
                "Namespace-level management",
                "Secure credential store",
                "Built-in variable store",
                "Centralized governance over task configuration",
                "Apps - Build custom UIs for workflows with forms and approval interfaces",
                "Unit Tests - Test flow behavior with fixtures and assertions to prevent regressions",
                "Versioned Plugins for dependency management",
                "AI Copilot with custom LLM providers",
                "Log Shipper for infrastructure-wide log forwarding",
                "Team-level storage and secrets isolation",
                "User invitations and announcements",
                "Service Accounts",
                "Maintenance Mode",
                "Backup & Restore",
                "Private networking and VPC peering",
                "Unlimited log retention",
                "Deploy on-prem or to any cloud, any region",
                "Enterprise support with guaranteed SLAs",
                "Onboarding & training"
            ],
            button: {
                text: "Talk to us",
                href: "https://kestra.io/demo?utm_source=app$&utm_campaign=enterprise&utm_content=stats"
            }
        },
        {
            name: "Kestra Cloud",
            price: "Custom price",
            features: [
                "Fully-managed version of the Enterprise Edition",
                "Almost all Enterprise Edition features with convenience of the cloud",
                "Automatic updates, backups, and infrastructure monitoring",
                "Managed worker pools and curated plugin environment",
                "Built-in Google, Microsoft, or Basic Authentication",
                "US & EU deployment regions on GCP",
                "Infrastructure hosted and managed by the Kestra team with SLA guarantees and custom support"
            ],
            button: {
                text: "Join Early Adopter Program",
                href: "https://kestra.io/cloud?utm_source=app&utm_campaign=cloud&utm_content=stats"
            }
        }
    ]
</script>

<style scoped lang="scss">
.edition-col {
    margin-bottom: 2rem;

    &:last-child {
        margin-bottom: 0;
    }

    @media (min-width: 992px) {
        margin-bottom: 0;
    }
}
</style>