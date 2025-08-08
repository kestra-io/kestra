<template>
    <el-row :gutter="32">
        <el-col :span="8" v-for="characteristics in editionCharacteristics" :key="characteristics.name">
            <edition-characteristics
                class="h-100"
                :name="characteristics.name"
                :price="characteristics.price"
                :features="characteristics.features"
                :button="characteristics.button"
            />
        </el-col>
    </el-row>
</template>
<script setup>
    import EditionCharacteristics from "./EditionCharacteristics.vue";
</script>
<script>
    export default {
        data() {
            return {
                editionCharacteristics: [
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
                            "Access to 400+ plugins",
                            "Git & CI/CD integrations",
                            "Basic authentication",
                            "Community support"
                        ],
                        button: {
                            text: "Current version"
                        }
                    },
                    {
                        name: "Enterprise Edition",
                        price: "Custom",
                        features: [
                            "All features from the Open-Source Edition and more",
                            "Scalable architecture with High-Availability",
                            "Multi-tenancy",
                            "Worker groups supporting distributed workers",
                            "Custom secrets backend (AWS Secret Manager, Azure Key Vault, Elasticsearch, Google Secret Manager, Hashicorp Vault)",
                            "Audit logs",
                            "Single Sign-On (SSO)",
                            "Role-Based Access Control (RBAC)",
                            "Custom blueprints",
                            "Namespace-level management",
                            "Secure credential store",
                            "Built-in variable store",
                            "Centralized governance over task configuration",
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
                        features: [
                            "Fully-managed version of the Enterprise Edition",
                            "All Enterprise-grade features with convenience of the cloud",
                            "Infrastructure hosted and managed by the Kestra team with SLA guarantees and custom support."
                        ],
                        button: {
                            text: "Sign up for a private Alpha",
                            href: "https://kestra.io/cloud?utm_source=app&utm_campaign=cloud&utm_content=stats"
                        }
                    }
                ]
            }
        }
    };
</script>