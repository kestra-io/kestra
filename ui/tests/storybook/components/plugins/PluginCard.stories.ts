import type {Meta, StoryObj} from "@storybook/vue3-vite"
import PluginCard from "../../../../src/components/plugins/PluginCard.vue"
import Database from "vue-material-design-icons/Database.vue"
import Creation from "vue-material-design-icons/Creation.vue"
import CloudOutline from "vue-material-design-icons/CloudOutline.vue"
import SourceBranch from "vue-material-design-icons/SourceBranch.vue"
import CogOutline from "vue-material-design-icons/CogOutline.vue"

const meta: Meta<typeof PluginCard> = {
    title: "Components/Plugins/PluginCard",
    component: PluginCard,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component:
                    "PluginCard renders a plugin / subgroup / task / blueprint card with optional icon, categories, and task/blueprint counts. The card is clickable as a whole and emits `@click`. Slots: `#icon` overrides the header icon, `#footer-content` overrides the counts area in the footer (chevron stays). Information shown adapts to the props provided — missing counts, categories, or description are simply omitted.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof PluginCard>

const wrap = (inner: string) => `
    <div style="padding:24px;max-width:320px">
        ${inner}
    </div>
`

export const Default: Story = {
    args: {
        title: "BigQuery",
        description: "Query, load and export data with Google BigQuery.",
        categories: ["DATABASE", "CLOUD"],
        taskCount: 12,
        blueprintCount: 4,
    },
    render: (args) => ({
        components: {PluginCard, Database},
        setup() { return {args} },
        template: wrap(`
            <PluginCard v-bind="args">
                <template #icon><Database :size="28" /></template>
            </PluginCard>
        `),
    }),
}

export const WithoutCounts: Story = {
    args: {
        title: "OpenAI",
        description: "Interact with OpenAI's GPT and embeddings APIs.",
        categories: ["AI"],
    },
    render: (args) => ({
        components: {PluginCard, Creation},
        setup() { return {args} },
        template: wrap(`
            <PluginCard v-bind="args">
                <template #icon><Creation :size="28" /></template>
            </PluginCard>
        `),
    }),
}

export const TaskCountOnly: Story = {
    args: {
        title: "Subflow",
        description: "Trigger a subflow from this flow.",
        taskCount: 1,
    },
    render: (args) => ({
        components: {PluginCard, SourceBranch},
        setup() { return {args} },
        template: wrap(`
            <PluginCard v-bind="args">
                <template #icon><SourceBranch :size="28" /></template>
            </PluginCard>
        `),
    }),
}

export const BlueprintLike: Story = {
    args: {
        title: "ETL pipeline",
        description: "Extract from Postgres, transform with Python, load into Snowflake.",
    },
    render: (args) => ({
        components: {PluginCard, SourceBranch},
        setup() { return {args} },
        template: wrap(`
            <PluginCard v-bind="args">
                <template #icon><SourceBranch :size="28" /></template>
            </PluginCard>
        `),
    }),
}

export const LongContent: Story = {
    args: {
        title: "A very very very long plugin title that needs to be truncated",
        description: "A description that wraps over two lines and gets clamped if it exceeds the available height, demonstrating overflow handling for description text.",
        categories: ["DATABASE", "CLOUD", "INGESTION", "MESSAGING"],
        taskCount: 42,
        blueprintCount: 15,
    },
    render: (args) => ({
        components: {PluginCard, Database},
        setup() { return {args} },
        template: wrap(`
            <PluginCard v-bind="args">
                <template #icon><Database :size="28" /></template>
            </PluginCard>
        `),
    }),
}

export const NotClickable: Story = {
    args: {
        title: "Read-only card",
        description: "A card without click affordance — no chevron, no hover effect.",
        taskCount: 8,
        clickable: false,
    },
    render: (args) => ({
        components: {PluginCard, CogOutline},
        setup() { return {args} },
        template: wrap(`
            <PluginCard v-bind="args">
                <template #icon><CogOutline :size="28" /></template>
            </PluginCard>
        `),
    }),
}

export const Blueprints: Story = {
    args: {
        title: "AI agent that routes to the right automation",
        description: "Captures a user's high-level intent and dispatches it to the matching workflow via tool calling.",
    },
    render: (args) => ({
        components: {PluginCard},
        setup() {
            return {args, icons: [Creation, Database, CloudOutline, SourceBranch]}
        },
        template: wrap(`
            <PluginCard v-bind="args">
                <template #footer-content>
                    <div style="display:flex;align-items:center;gap:8px;flex:1 1 auto;min-width:0;overflow:hidden">
                        <span v-for="(Icon, i) in icons" :key="i" style="
                            width:24px;height:24px;display:inline-flex;align-items:center;justify-content:center;
                            background-color:var(--ks-bg-tag);border-radius:var(--ks-radius-base);
                        ">
                            <component :is="Icon" :size="18" />
                        </span>
                        <span style="
                            width:24px;height:24px;display:inline-flex;align-items:center;justify-content:center;
                            background-color:var(--ks-bg-tag);border-radius:var(--ks-radius-base);
                            font-size:var(--ks-font-size-xs);font-weight:600;color:var(--ks-text-primary);
                        ">+3</span>
                    </div>
                </template>
            </PluginCard>
        `),
    }),
}
